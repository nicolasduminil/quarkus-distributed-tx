package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import io.quarkus.hibernate.orm.panache.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;

import java.util.*;

/**
 * JPA/Panache implementation over the {@code accounts} XA datasource. Enlisting
 * this datasource in the caller's JTA transaction is what lets Narayana run a
 * two-phase commit across the accounts and xfers databases.
 */
@ApplicationScoped
@Alternative
public class AccountRepositoryXA implements PanacheRepositoryBase<Account, String>, AccountRepository
{
  @Override
  public Optional<Account> getAccount(String accountId)
  {
    return findByIdOptional(accountId);
  }

  @Override
  public List<Account> getAccounts()
  {
    return listAll();
  }

  @Override
  public void createAccount(Account account)
  {
    persist(account);
  }

  @Override
  public void updateAccount(Account account)
  {
    update("holder = ?1, balance = ?2, currency = ?3 where id = ?4",
      account.getHolder(), account.getBalance(), account.getCurrency(), account.getId());
  }

  @Override
  public void deleteAccount(String accountId)
  {
    deleteById(accountId);
  }
}
