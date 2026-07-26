package fr.simplex_software.quarkus_xa.money_xfr.account.tests;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import io.quarkus.test.junit.*;
import io.restassured.http.*;
import org.apache.http.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AccountResourceIT
{
  private BigDecimal balanceOf(String accountId)
  {
    return new BigDecimal(given().accept(ContentType.JSON)
      .when().get("/accounts/{id}", accountId)
      .then().statusCode(HttpStatus.SC_OK)
      .extract().jsonPath().getString("balance"));
  }

  @Test
  @Order(10)
  public void shouldReturnSeededAccounts()
  {
    given().accept(ContentType.JSON)
      .when().get("/accounts")
      .then().statusCode(HttpStatus.SC_OK)
      .body("accountId", hasItems("ACC1", "ACC2"));
  }

  @Test
  @Order(20)
  public void shouldReturn404ForUnknownAccount()
  {
    given().accept(ContentType.JSON)
      .when().get("/accounts/{id}", "UNKNOWN")
      .then().statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  @Order(30)
  public void shouldCreateReadUpdateDeleteAccount()
  {
    AccountDto account = new AccountDto("ACC9", "Carol", new BigDecimal("100.00"), "EUR");
    given().contentType(ContentType.JSON).body(account)
      .when().post("/accounts")
      .then().statusCode(HttpStatus.SC_CREATED);
    assertThat(balanceOf("ACC9")).isEqualByComparingTo("100.00");

    account.setCustomerName("Caroline");
    account.setBalance(new BigDecimal("250.00"));
    given().contentType(ContentType.JSON).body(account)
      .when().put("/accounts/{id}", "ACC9")
      .then().statusCode(HttpStatus.SC_OK);
    assertThat(balanceOf("ACC9")).isEqualByComparingTo("250.00");

    given().when().delete("/accounts/{id}", "ACC9")
      .then().statusCode(HttpStatus.SC_NO_CONTENT);
    given().accept(ContentType.JSON)
      .when().get("/accounts/{id}", "ACC9")
      .then().statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  @Order(40)
  public void shouldDebitAndCreditAccount()
  {
    given().contentType(ContentType.JSON).body(new BigDecimal("200.00"))
      .when().post("/accounts/{id}/debit", "ACC1")
      .then().statusCode(HttpStatus.SC_OK);
    assertThat(balanceOf("ACC1")).isEqualByComparingTo("800.00");

    given().contentType(ContentType.JSON).body(new BigDecimal("50.00"))
      .when().post("/accounts/{id}/credit", "ACC1")
      .then().statusCode(HttpStatus.SC_OK);
    assertThat(balanceOf("ACC1")).isEqualByComparingTo("850.00");
  }

  @Test
  @Order(50)
  public void shouldRejectOverdraftAndInvalidAmounts()
  {
    given().contentType(ContentType.JSON).body(new BigDecimal("999999.00"))
      .when().post("/accounts/{id}/debit", "ACC2")
      .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    given().contentType(ContentType.JSON).body(new BigDecimal("-1.00"))
      .when().post("/accounts/{id}/debit", "ACC2")
      .then().statusCode(HttpStatus.SC_BAD_REQUEST);
  }
}
