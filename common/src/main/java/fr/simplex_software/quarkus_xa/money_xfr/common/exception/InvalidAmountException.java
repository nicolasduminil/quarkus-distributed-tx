package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

public class InvalidAmountException extends ApplicationException
{
  public InvalidAmountException(String accountId)
  {
    super(Response.Status.BAD_REQUEST,
      "### An invalid amount (<= 0) has been requested for the account %s. We can't continue."
        .formatted(accountId));
  }
}
