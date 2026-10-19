package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

public class InsufficientFundsException extends ApplicationException
{
  public InsufficientFundsException(String accountId)
  {
    super(Response.Status.BAD_REQUEST, "### Insufficient funds on source account %s".formatted(accountId));
  }
}
