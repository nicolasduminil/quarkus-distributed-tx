package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

public abstract class ApplicationException extends RuntimeException
{
  private final Response.Status status;

  protected ApplicationException(Response.Status status, String message)
  {
    super(message);
    this.status = status;
  }

  public Response.Status getStatus()
  {
    return status;
  }
}
