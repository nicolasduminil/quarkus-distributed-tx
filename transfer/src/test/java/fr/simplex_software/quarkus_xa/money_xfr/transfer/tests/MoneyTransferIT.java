package fr.simplex_software.quarkus_xa.money_xfr.transfer.tests;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.tck.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.repository.*;
import io.quarkus.narayana.jta.*;
import io.quarkus.test.junit.*;
import jakarta.inject.*;

import java.math.*;

/**
 * The shared transfer contract, run against the XA/2PC implementation.
 * <p>
 * Because this service <em>is</em> the resource manager for both databases, it can
 * observe balances by injecting the repositories directly. The LRA counterpart
 * cannot — that difference is the whole point, and it is why the contract leaves
 * these probes abstract.
 */
@QuarkusTest
public class MoneyTransferIT extends AbstractMoneyTransferContractIT
{
  @Inject
  AccountRepository accountRepository;
  @Inject
  TransferRepository transferRepository;

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
    return QuarkusTransaction.requiringNew()
      .call(() -> accountRepository.getAccount(accountId).orElseThrow().getBalance());
  }

  /**
   * Under 2PC a rolled-back transfer leaves no row behind, so every row that
   * exists is by construction a completed one.
   */
  @Override
  protected int completedTransferCount()
  {
    return QuarkusTransaction.requiringNew().call(() -> transferRepository.getTransfers().size());
  }
}
