package fr.simplex_software.quarkus_xa.money_xfr.tck;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import io.restassured.http.*;
import jakarta.ws.rs.core.Response.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

/**
 * The REST contract that <em>both</em> transfer implementations must satisfy — the
 * XA/2PC one and the LRA/saga one. Keeping it in one place is what makes the
 * comparison between them meaningful: if a scenario passes here against both
 * stacks, the two are functionally equivalent and differ only in how they get
 * there.
 * <p>
 * Two deliberate design choices:
 * <ul>
 *   <li><b>Assertions are on deltas, not absolute balances.</b> The two stacks seed
 *       different accounts with different opening balances, so the contract reads
 *       the balance first and asserts how it moved.</li>
 *   <li><b>The probes are abstract.</b> The XA service owns the accounts datasource
 *       and reads balances straight from it; the LRA service has no such access
 *       <em>by design</em> and must ask the account service over HTTP. The
 *       scenarios are shared, the observation mechanism is not.</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractMoneyTransferContractIT
{
  protected static final BigDecimal AMOUNT = new BigDecimal("150.24");

  /** An account with at least {@link #AMOUNT} available. */
  protected abstract String sourceAccount();

  protected abstract String destinationAccount();

  /** Reads the settled balance, however this stack is able to observe it. */
  protected abstract BigDecimal balanceOf(String accountId);

  /**
   * How many transfers actually went through.
   * <p>
   * Deliberately <em>completed</em> transfers rather than all rows. A 2PC
   * rollback leaves no trace at all, whereas a cancelled saga records that it
   * undid itself — a COMPENSATED row is the honest outcome, not a failure. Counting
   * only completed transfers lets one contract hold for both without pretending
   * that difference away.
   */
  protected abstract int completedTransferCount();

  /**
   * XA commits before the response is written, so its effects are visible
   * immediately. A saga only closes once the coordinator has driven every
   * participant's completion, which may land just after the response. Polling
   * keeps one contract honest for both rather than baking a race into it.
   */
  protected void eventually(Runnable assertion)
  {
    AssertionError last = null;
    for (int attempt = 0; attempt < 50; attempt++)
    {
      try
      {
        assertion.run();
        return;
      }
      catch (AssertionError ae)
      {
        last = ae;
        try
        {
          Thread.sleep(100);
        }
        catch (InterruptedException ie)
        {
          Thread.currentThread().interrupt();
          throw ae;
        }
      }
    }
    throw last;
  }

  private String postTransfer(String source, String destination, BigDecimal amount, Status expected)
  {
    return given().contentType(ContentType.JSON)
      .body(new TransferRequest(source, destination, amount, "EUR"))
      .when().post("/xfers")
      .then().statusCode(expected.getStatusCode())
      .extract().body().asString();
  }

  /**
   * Asserts the caller is told <em>why</em> the transfer failed, not merely that it
   * did. Worth pinning: a saga has machinery of its own that will happily replace a
   * business error with its own plumbing message if the LRA is mishandled, and a
   * status-code-only assertion never notices.
   */
  private void assertBusinessError(String body)
  {
    assertThat(body)
      .as("the response body should carry the business error, not transaction plumbing")
      .contains("###")
      .doesNotContainIgnoringCase("LRA")
      .doesNotContainIgnoringCase("CancelFailed");
  }

  /**
   * Picks a COMPLETED transfer rather than simply the first one: the saga stack's
   * listing may also contain COMPENSATED rows, and their order is unspecified.
   */
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

    // XA rolled both datasources back; the saga released its reservations. The
    // mechanisms differ completely, the observable outcome must not.
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
