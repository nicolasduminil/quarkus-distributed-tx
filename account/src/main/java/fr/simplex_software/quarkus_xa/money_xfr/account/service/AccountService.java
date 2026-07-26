package fr.simplex_software.quarkus_xa.money_xfr.account.service;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;

import java.math.*;
import java.util.*;

@ApplicationScoped
public class AccountService
{
  @Inject
  AccountRepository accountRepository;

  public AccountDto getAccount(String accountId)
  {
    return accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId))
      .toDto();
  }

  public List<AccountDto> getAccounts()
  {
    return accountRepository.getAccounts().stream().map(Account::toDto).toList();
  }

  @Transactional
  public void createAccount(AccountDto accountDto)
  {
    accountRepository.createAccount(new Account(accountDto));
  }

  @Transactional
  public void updateAccount(String accountId, AccountDto accountDto)
  {
    accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId));
    accountDto.setAccountId(accountId);
    accountRepository.updateAccount(new Account(accountDto));
  }

  @Transactional
  public void deleteAccount(String accountId)
  {
    accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId));
    accountRepository.deleteAccount(accountId);
  }

  @Transactional
  public void debit(String accountId, BigDecimal amount)
  {
    Account account = accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (amount.compareTo(account.getBalance()) > 0)
      throw new InsufficientFundsException(accountId);
    account.debit(amount);
  }

  @Transactional
  public void credit(String accountId, BigDecimal amount)
  {
    Account account = accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId));
    account.credit(amount);
  }
}
