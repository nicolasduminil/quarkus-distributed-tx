package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.exception;

import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

/**
 * Re-surfaces a failure from the account service under this service's own error
 * contract, so that a saga transfer answers with the same status code the XA
 * transfer would have produced (400 for insufficient funds, 404 for an unknown
 * account). Without this, the caller would see whatever the REST client happened
 * to throw and the shared contract test could not hold for both stacks.
 */
public class RemoteAccountException extends ApplicationException
{
  public RemoteAccountException(Response.Status status, String message)
  {
    super(status, message);
  }

  public static RemoteAccountException from(WebApplicationException wae)
  {
    Response response = wae.getResponse();
    Response.Status status = Response.Status.fromStatusCode(response.getStatus());
    // Anything the account service returns that is not a recognised client error
    // is our problem to report, not the caller's fault.
    if (status == null || status.getFamily() != Response.Status.Family.CLIENT_ERROR)
      status = Response.Status.BAD_GATEWAY;
    return new RemoteAccountException(status, readMessage(response, wae));
  }

  private static String readMessage(Response response, WebApplicationException fallback)
  {
    try
    {
      String body = response.hasEntity() ? response.readEntity(String.class) : null;
      return body == null || body.isBlank() ? fallback.getMessage() : body;
    }
    catch (RuntimeException e)
    {
      return fallback.getMessage();
    }
  }
}
