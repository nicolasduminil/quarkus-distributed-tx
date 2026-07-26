package fr.simplex_software.quarkus_xa.money_xfr.transfer.tests;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.repository.*;
import io.quarkus.narayana.jta.*;
import io.quarkus.test.junit.*;
import io.restassured.http.*;
import jakarta.inject.*;
import org.apache.http.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MoneyTransferIT
{
  @Inject
  AccountRepository accountRepository;
  @Inject
  TransferRepository transferRepository;

  private BigDecimal balanceOf(String accountId)
  {
    return QuarkusTransaction.requiringNew()
      .call(() -> accountRepository.getAccount(accountId).orElseThrow().getBalance());
  }

  private int transferCount()
  {
    return QuarkusTransaction.requiringNew().call(() -> transferRepository.getTransfers().size());
  }

  @Test
  @Order(10)
  public void transferShouldDebitSourceAndCreditDestination()
  {
    given().contentType(ContentType.JSON)
      .body(new TransferRequest("ACC123", "ACC321", new BigDecimal("150.24"), "EUR"))
      .when().post("/xfers")
      .then().statusCode(HttpStatus.SC_OK);
    // Both XA datasources committed together.
    assertThat(balanceOf("ACC123")).isEqualByComparingTo("849.76");
    assertThat(balanceOf("ACC321")).isEqualByComparingTo("1150.24");
    assertThat(transferCount()).isEqualTo(1);
  }

  @Test
  @Order(20)
  public void failedTransferShouldRollBackBothDatasources()
  {
    // ACC123 only holds 849.76 now, so 5000 overdraws it -> InsufficientFundsException.
    given().contentType(ContentType.JSON)
      .body(new TransferRequest("ACC123", "ACC321", new BigDecimal("5000.00"), "EUR"))
      .when().post("/xfers")
      .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    // Neither the balances (accounts DS) nor the transfer log (xfers DS) changed.
    assertThat(balanceOf("ACC123")).isEqualByComparingTo("849.76");
    assertThat(balanceOf("ACC321")).isEqualByComparingTo("1150.24");
    assertThat(transferCount()).isEqualTo(1);
  }

  @Test
  @Order(30)
  public void unknownAccountShouldReturn404()
  {
    given().contentType(ContentType.JSON)
      .body(new TransferRequest("NOPE", "ACC321", new BigDecimal("10.00"), "EUR"))
      .when().post("/xfers")
      .then().statusCode(HttpStatus.SC_NOT_FOUND);
    assertThat(transferCount()).isEqualTo(1);
  }

  @Test
  @Order(40)
  public void shouldListAndGetTransfers()
  {
    String transferId = given().accept(ContentType.JSON)
      .when().get("/xfers")
      .then().statusCode(HttpStatus.SC_OK)
      .extract().jsonPath().getString("[0].transferId");
    assertThat(transferId).isNotBlank();
    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", transferId)
      .then().statusCode(HttpStatus.SC_OK)
      .body("status", org.hamcrest.Matchers.equalTo("COMPLETED"));
    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", "does-not-exist")
      .then().statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  @Order(50)
  public void shouldDeleteTransfer()
  {
    String transferId = given().accept(ContentType.JSON)
      .when().get("/xfers")
      .then().statusCode(HttpStatus.SC_OK)
      .extract().jsonPath().getString("[0].transferId");
    given().when().delete("/xfers/{id}", transferId)
      .then().statusCode(HttpStatus.SC_NO_CONTENT);
    given().accept(ContentType.JSON)
      .when().get("/xfers/{id}", transferId)
      .then().statusCode(HttpStatus.SC_NOT_FOUND);
    assertThat(transferCount()).isZero();
  }
}
