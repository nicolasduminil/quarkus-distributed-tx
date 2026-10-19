package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.responses.*;
import org.eclipse.microprofile.openapi.annotations.tags.*;

import java.math.*;
import java.net.*;

@ApplicationScoped
@Path("/lra/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Account LRA Participant", description = "Saga participant endpoints using reserve-then-confirm")
public class AccountLraResource
{
  @Inject
  AccountLraService accountLraService;

  @Operation(summary = "Reserve a debit",
    description = "Records a reservation for the amount without moving the balance. The reservation is applied on @Complete and dropped on @Compensate.")
  @APIResponse(responseCode = "200", description = "Amount reserved")
  @APIResponse(responseCode = "400", description = "Invalid amount, or insufficient available funds")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  @Path("/{accountId}/debit")
  @LRA(value = LRA.Type.MANDATORY, end = false, cancelOnFamily = {})
  public Response debit(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId,
                        @PathParam("accountId") String accountId, BigDecimal amount)
  {
    accountLraService.reserveDebit(lraId.toASCIIString(), accountId, amount);
    return Response.ok().build();
  }

  @Operation(summary = "Reserve a credit",
    description = "Records the incoming amount without moving the balance, so money never appears and then vanishes if the saga is cancelled.")
  @APIResponse(responseCode = "200", description = "Amount reserved")
  @APIResponse(responseCode = "400", description = "Invalid amount")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  @Path("/{accountId}/credit")
  @LRA(value = LRA.Type.MANDATORY, end = false, cancelOnFamily = {})
  public Response credit(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId,
                         @PathParam("accountId") String accountId, BigDecimal amount)
  {
    accountLraService.reserveCredit(lraId.toASCIIString(), accountId, amount);
    return Response.ok().build();
  }

  @Operation(summary = "Available balance",
    description = "Settled balance, amount held by in-flight sagas, and what is actually spendable.")
  @GET
  @Path("/{accountId}/availability")
  public Response availability(@PathParam("accountId") String accountId)
  {
    return Response.ok(accountLraService.availability(accountId)).build();
  }

  @PUT
  @Path("/complete")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Complete
  public Response complete(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return Response.ok(accountLraService.complete(lraId.toASCIIString()).name()).build();
  }

  @PUT
  @Path("/compensate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Compensate
  public Response compensate(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return Response.ok(accountLraService.compensate(lraId.toASCIIString()).name()).build();
  }

  @GET
  @Path("/status")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Status
  public Response status(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return accountLraService.status(lraId.toASCIIString())
      .map(status -> Response.ok(status.name()).build())
      .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @DELETE
  @Path("/forget")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Forget
  public Response forget(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    accountLraService.forget(lraId.toASCIIString());
    return Response.ok().build();
  }
}
