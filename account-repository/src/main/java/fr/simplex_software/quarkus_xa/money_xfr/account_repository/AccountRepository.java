package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import java.util.*;

/**
 * The account repository, shared by both services. Two implementations are
 * provided as CDI {@code @Alternative}s: {@link AccountRepositoryXA} (JPA/Panache
 * over the {@code accounts} XA datasource, the one selected in production) and
 * {@link DefaultAccountRepository} (in-memory). Pick one per application via
 * {@code quarkus.arc.selected-alternatives}.
 */
public interface AccountRepository
{
  Optional<Account> getAccount(String accountId);
  List<Account> getAccounts();
  void createAccount(Account account);
  void updateAccount(Account account);
  void deleteAccount(String accountId);
}
