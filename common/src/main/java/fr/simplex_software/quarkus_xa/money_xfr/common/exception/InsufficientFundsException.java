package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

/**
 * Thrown when a debit would overdraw the source account. Being a
 * {@link RuntimeException}, it triggers the default JTA rollback, so every XA
 * resource enlisted in the current transaction (the accounts datasource and
 * the xfers datasource) is rolled back atomically.
 */
public class InsufficientFundsException extends ApplicationException
{
  public InsufficientFundsException(String accountId)
  {
    super(Response.Status.BAD_REQUEST, "### Insufficient funds on source account %s".formatted(accountId));
  }
}
