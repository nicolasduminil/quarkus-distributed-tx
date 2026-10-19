package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.api;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.service.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.media.*;
import org.eclipse.microprofile.openapi.annotations.responses.*;
import org.eclipse.microprofile.openapi.annotations.tags.*;

import java.net.*;
import java.util.*;

@ApplicationScoped
@Path("/xfers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Money Transfer Management (Saga)", description = "Money transfers orchestrated as a MicroProfile LRA saga")
public class TransferResource
{
  @Inject
  TransferSagaService transferSagaService;

  @Operation(summary = "Create a money transfer",
    description = "Reserves a debit on the source and a credit on the destination through the account service, then closes the LRA so both are applied together")
  @APIResponse(responseCode = "200", description = "Transfer accepted and the saga closed",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid or insufficient amount")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  @LRA(value = LRA.Type.REQUIRED)
  public Response createTransfer(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId, TransferRequest request)
  {
    Objects.requireNonNull(request);
    TransferDto transfer = transferSagaService.processTransfer(lraId.toASCIIString(), request);
    return Response.ok(TransferResponse.fromTransfer(transfer)).build();
  }

  @PUT
  @Path("/lra/complete")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Complete
  public Response complete(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return Response.ok(transferSagaService.complete(lraId.toASCIIString()).name()).build();
  }

  @PUT
  @Path("/lra/compensate")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Compensate
  public Response compensate(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return Response.ok(transferSagaService.compensate(lraId.toASCIIString()).name()).build();
  }

  @GET
  @Path("/lra/status")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Status
  public Response status(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    return transferSagaService.status(lraId.toASCIIString())
      .map(status -> Response.ok(status.name()).build())
      .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @DELETE
  @Path("/lra/forget")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.TEXT_PLAIN)
  @Forget
  public Response forget(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) URI lraId)
  {
    transferSagaService.forget(lraId.toASCIIString());
    return Response.ok().build();
  }

  @Operation(summary = "Get transfer details", description = "Retrieves a transfer by its ID")
  @APIResponse(responseCode = "200", description = "Transfer found successfully",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferDto.class)))
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @GET
  @Path("/{transferId}")
  public Response getTransfer(@PathParam("transferId") String transferId)
  {
    return Response.ok(transferSagaService.getTransfer(transferId)).build();
  }

  @Operation(summary = "Get transfers", description = "Retrieves the list of the existing transfers, including compensated ones")
  @APIResponse(responseCode = "200", description = "Success",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferDto.class)))
  @GET
  public Response getTransfers()
  {
    return Response.ok(transferSagaService.getTransfers()).build();
  }

  @Operation(summary = "Update transfer", description = "Updates an existing transfer's information")
  @APIResponse(responseCode = "200", description = "Transfer updated successfully")
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @PUT
  @Path("/{transferId}")
  public Response updateTransfer(@PathParam("transferId") String transferId, TransferDto transferDto)
  {
    Objects.requireNonNull(transferDto);
    transferSagaService.updateTransfer(transferId, transferDto);
    return Response.ok().build();
  }

  @Operation(summary = "Delete transfer", description = "Deletes an existing transfer")
  @APIResponse(responseCode = "204", description = "Transfer deleted successfully")
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @DELETE
  @Path("/{transferId}")
  public Response deleteTransfer(@PathParam("transferId") String transferId)
  {
    transferSagaService.deleteTransfer(transferId);
    return Response.noContent().build();
  }
}
