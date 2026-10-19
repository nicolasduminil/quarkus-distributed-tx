package fr.simplex_software.quarkus_xa.money_xfr.tck;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import io.restassured.http.*;
import jakarta.ws.rs.core.Response.*;
import org.awaitility.core.*;
import org.junit.jupiter.api.*;

import java.math.*;
import java.time.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
// Imported by name rather than with a wildcard: Awaitility also declares given(),
// which would collide with RestAssured's.
import static org.awaitility.Awaitility.await;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractMoneyTransferContractIT
{
  protected static final BigDecimal AMOUNT = new BigDecimal("150.24");
  private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  protected abstract String sourceAccount();

  protected abstract String destinationAccount();

  protected abstract BigDecimal balanceOf(String accountId);

  protected abstract int completedTransferCount();

  protected void eventually(ThrowingRunnable assertion)
  {
    await().atMost(SETTLE_TIMEOUT)
      .pollDelay(Duration.ZERO)
      .pollInterval(POLL_INTERVAL)
      .untilAsserted(assertion);
  }

  private String postTransfer(String source, String destination, BigDecimal amount, Status expected)
  {
    return given().contentType(ContentType.JSON)
      .body(new TransferRequest(source, destination, amount, "EUR"))
      .when().post("/xfers")
      .then().statusCode(expected.getStatusCode())
      .extract().body().asString();
  }

  private void assertBusinessError(String body)
  {
    assertThat(body)
      .as("the response body should carry the business error, not transaction plumbing")
      .contains("###")
      .doesNotContainIgnoringCase("LRA")
      .doesNotContainIgnoringCase("CancelFailed");
  }

  private String aCompletedTransferId()
  {
    return given().accept(ContentType.JSON)
      .when().get("/xfers")
      .then().statusCode(Status.OK.getStatusCode())
      .extract().jsonPath()
      .getString("find { it.status == '%s' }.transferId".formatted(TransferStatus.COMPLETED.name()));
  }

  @Test
  @Order(10)
  public void transferShouldDebitSourceAndCreditDestination()
  {
    BigDecimal sourceBefore = balanceOf(sourceAccount());
    BigDecimal destinationBefore = balanceOf(destinationAccount());
    int countBefore = completedTransferCount();

    postTransfer(sourceAccount(), destinationAccount(), AMOUNT, Status.OK);

    eventually(() ->
    {
      assertThat(balanceOf(sourceAccount())).isEqualByComparingTo(sourceBefore.subtract(AMOUNT));
      assertThat(balanceOf(destinationAccount())).isEqualByComparingTo(destinationBefore.add(AMOUNT));
      assertThat(completedTransferCount()).isEqualTo(countBefore + 1);
    });
  }

  @Test
  @Order(20)
  public void insufficientFundsShouldLeaveEverythingUnchanged()
  {
    BigDecimal sourceBefore = balanceOf(sourceAccount());
    BigDecimal destinationBefore = balanceOf(destinationAccount());
    int countBefore = completedTransferCount();

    assertBusinessError(postTransfer(sourceAccount(), destinationAccount(),
      sourceBefore.add(new BigDecimal("1000000.00")), Status.BAD_REQUEST));

    eventually(() ->
    {
      assertThat(balanceOf(sourceAccount())).isEqualByComparingTo(sourceBefore);
      assertThat(balanceOf(destinationAccount())).isEqualByComparingTo(destinationBefore);
      assertThat(completedTransferCount()).isEqualTo(countBefore);
    });
  }

  @Test
  @Order(30)
  public void unknownAccountShouldReturn404()
  {
    int countBefore = completedTransferCount();
    assertBusinessError(postTransfer("NO-SUCH-ACCOUNT", destinationAccount(), AMOUNT, Status.NOT_FOUND));
    eventually(() -> assertThat(completedTransferCount()).isEqualTo(countBefore));
  }

  @Test
  @Order(40)
  public void shouldListAndGetTransfers()
  {
    String transferId = aCompletedTransferId();
    assertThat(transferId).isNotBlank();

    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", transferId)
      .then().statusCode(Status.OK.getStatusCode())
      .body("status", org.hamcrest.Matchers.equalTo(TransferStatus.COMPLETED.name()));

    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", "does-not-exist")
      .then().statusCode(Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Order(50)
  public void shouldDeleteTransfer()
  {
    int countBefore = completedTransferCount();
    String transferId = aCompletedTransferId();

    given().when().delete("/xfers/{id}", transferId)
      .then().statusCode(Status.NO_CONTENT.getStatusCode());
    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", transferId)
      .then().statusCode(Status.NOT_FOUND.getStatusCode());

    assertThat(completedTransferCount()).isEqualTo(countBefore - 1);
  }
}
