package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;

/**
 * Base class for business exceptions that map to an HTTP response. Each subclass
 * declares the status it maps to, so a single {@link ApplicationExceptionMapper}
 * can turn any of them into a response — no per-exception mapper needed.
 */
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
