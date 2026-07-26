package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;

import java.util.*;

/**
 * In-memory implementation, useful for demos/tests without a database. It is the
 * CDI {@code @Default} alternative; select {@link AccountRepositoryXA} instead
 * via {@code quarkus.arc.selected-alternatives} for the real XA behaviour.
 */
@ApplicationScoped
@Alternative
@Default
public class DefaultAccountRepository implements AccountRepository
{
  private static final Map<String, Account> accounts = new HashMap<>();

  @Override
  public Optional<Account> getAccount(String accountId)
  {
    return Optional.ofNullable(accounts.get(accountId));
  }

  @Override
  public List<Account> getAccounts()
  {
    return new ArrayList<>(accounts.values());
  }

  @Override
  public void createAccount(Account account)
  {
    accounts.put(account.getId(), account);
  }

  @Override
  public void updateAccount(Account account)
  {
    accounts.put(account.getId(), account);
  }

  @Override
  public void deleteAccount(String accountId)
  {
    accounts.remove(accountId);
  }
}
