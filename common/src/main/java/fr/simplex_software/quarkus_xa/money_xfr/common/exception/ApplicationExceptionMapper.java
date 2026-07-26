package fr.simplex_software.quarkus_xa.money_xfr.common.exception;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

/**
 * Single mapper for every {@link ApplicationException}. JAX-RS resolves the
 * nearest-supertype mapper, so this handles all subclasses (in either service)
 * and each one contributes its own status code.
 */
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
