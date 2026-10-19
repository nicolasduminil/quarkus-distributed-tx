package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.exception;

import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

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
