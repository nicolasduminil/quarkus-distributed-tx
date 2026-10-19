package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.tests;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.tck.*;
import io.quarkus.test.junit.*;
import io.restassured.http.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

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

  @Test
  @Order(80)
  public void statusOfAnUnknownLraShouldBe404()
  {
    given().header("Long-Running-Action", "http://localhost:50000/lra-coordinator/no-such-lra")
      .when().get("/lra/accounts/status")
      .then().statusCode(404);
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
