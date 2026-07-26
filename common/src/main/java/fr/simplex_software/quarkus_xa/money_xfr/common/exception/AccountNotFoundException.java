package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

public class AccountNotFoundException extends ApplicationException
{
  public AccountNotFoundException(String accountId)
  {
    super(Response.Status.NOT_FOUND, "### Account with ID %s hasn't been found.".formatted(accountId));
  }
}
