package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.tests;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.tck.*;
import io.quarkus.test.junit.*;
import io.restassured.http.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

/**
 * The shared transfer contract, run against the LRA/saga implementation.
 * <p>
 * The probes are the interesting part. Where the XA test injects the repositories
 * and reads the databases directly, this one can only ask over HTTP — because
 * that is genuinely all a saga orchestrator has. The same scenarios pass either
 * way, which is the point: the two stacks are functionally equivalent.
 */
@QuarkusTest
public class MoneyTransferSagaIT extends AbstractMoneyTransferContractIT
{
  @Override
  protected String sourceAccount()
  {
    return "ACC123";
  }

  @Override
  protected String destinationAccount()
  {
    return "ACC321";
  }

  @Override
  protected BigDecimal balanceOf(String accountId)
  {
    return new BigDecimal(given().accept(ContentType.JSON)
      .when().get("/accounts/{id}", accountId)
      .then().statusCode(200)
      .extract().jsonPath().getString("balance"));
  }

  @Override
  protected int completedTransferCount()
  {
    return given().accept(ContentType.JSON)
      .when().get("/xfers")
      .then().statusCode(200)
      .extract().jsonPath()
      .getList("findAll { it.status == '%s' }".formatted(TransferStatus.COMPLETED.name()))
      .size();
  }

  /**
   * Saga-specific, and the reason the whole exercise is worth doing: a cancelled
   * saga does not vanish the way a 2PC rollback does. It leaves a COMPENSATED
   * record saying "this was attempted and undone" — an audit trail XA cannot
   * produce, because a rolled-back transaction leaves no rows at all.
   */
  @Test
  @Order(60)
  public void cancelledSagaShouldLeaveACompensatedRecord()
  {
    given().contentType(ContentType.JSON)
      .body(new TransferRequest(sourceAccount(), destinationAccount(),
        new BigDecimal("9999999.00"), "EUR"))
      .when().post("/xfers")
      .then().statusCode(400);

    eventually(() -> assertThat(compensatedTransferCount()).isPositive());
  }

  /**
   * The other half of the contrast: a hold is observable while the saga is in
   * flight. Under XA there is no such window — nothing is visible until commit.
   * Here we only assert the mechanism exists and nets out to zero once the saga
   * closes, which is what makes the reserve-then-confirm model safe.
   */
  @Test
  @Order(70)
  public void settledSagaShouldLeaveNoHoldsBehind()
  {
    eventually(() ->
    {
      BigDecimal held = new BigDecimal(given().accept(ContentType.JSON)
        .when().get("/lra/accounts/{id}/availability", sourceAccount())
        .then().statusCode(200)
        .extract().jsonPath().getString("held"));
      assertThat(held).isEqualByComparingTo(BigDecimal.ZERO);
    });

    given().accept(ContentType.JSON)
      .when().get("/lra/accounts/{id}/availability", sourceAccount())
      .then().statusCode(200)
      .body("available", org.hamcrest.Matchers.notNullValue());
  }

  private int compensatedTransferCount()
  {
    return given().accept(ContentType.JSON)
      .when().get("/xfers")
      .then().statusCode(200)
      .extract().jsonPath()
      .getList("findAll { it.status == '%s' }".formatted(TransferStatus.COMPENSATED.name()))
      .size();
  }
}
