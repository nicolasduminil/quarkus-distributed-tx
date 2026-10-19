package fr.simplex_software.quarkus_xa.money_xfr.transfer.tests;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.tck.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.repository.*;
import io.quarkus.narayana.jta.*;
import io.quarkus.test.junit.*;
import jakarta.inject.*;

import java.math.*;

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

  @Override
  protected int completedTransferCount()
  {
    return QuarkusTransaction.requiringNew().call(() -> transferRepository.getTransfers().size());
  }
}
