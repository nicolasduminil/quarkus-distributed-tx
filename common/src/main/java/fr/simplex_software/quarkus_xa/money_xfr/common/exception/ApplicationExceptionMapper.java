package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

@Provider
public class ApplicationExceptionMapper implements ExceptionMapper<ApplicationException>
{
  @Override
  public Response toResponse(ApplicationException exception)
  {
    return Response.status(exception.getStatus())
      .entity(exception.getMessage())
      .build();
  }
}
