package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import java.util.*;

public interface AccountRepository
{
  Optional<Account> getAccount(String accountId);
  List<Account> getAccounts();
  void createAccount(Account account);
  void updateAccount(Account account);
  void deleteAccount(String accountId);
}
