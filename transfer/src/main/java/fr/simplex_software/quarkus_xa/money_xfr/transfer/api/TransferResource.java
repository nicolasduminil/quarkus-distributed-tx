package fr.simplex_software.quarkus_xa.money_xfr.transfer.api;

import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.service.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.media.*;
import org.eclipse.microprofile.openapi.annotations.responses.*;
import org.eclipse.microprofile.openapi.annotations.tags.*;

import java.util.*;

@ApplicationScoped
@Path("/xfers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Money Transfer Management", description = "Operations for managing money transfers")
public class TransferResource
{
  @Inject
  TransferService transferService;

  @Operation(summary = "Create a money transfer",
    description = "Debits the source account and credits the destination account atomically (XA/2PC)")
  @APIResponse(responseCode = "200", description = "Transfer processed successfully",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid or insufficient amount")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  public Response createTransfer(TransferRequest request)
  {
    Objects.requireNonNull(request);
    TransferDto transfer = transferService.processTransfer(request);
    return Response.ok(TransferResponse.fromTransfer(transfer)).build();
  }

  @Operation(summary = "Get transfer details", description = "Retrieves a transfer by its ID")
  @APIResponse(responseCode = "200", description = "Transfer found successfully",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferDto.class)))
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @GET
  @Path("/{transferId}")
  public Response getTransfer(@PathParam("transferId") String transferId)
  {
    return Response.ok(transferService.getTransfer(transferId)).build();
  }

  @Operation(summary = "Get transfers", description = "Retrieves the list of the existing transfers")
  @APIResponse(responseCode = "200", description = "Success",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransferDto.class)))
  @GET
  public Response getTransfers()
  {
    return Response.ok(transferService.getTransfers()).build();
  }

  @Operation(summary = "Update transfer", description = "Updates an existing transfer's information")
  @APIResponse(responseCode = "200", description = "Transfer updated successfully")
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @PUT
  @Path("/{transferId}")
  public Response updateTransfer(@PathParam("transferId") String transferId, TransferDto transferDto)
  {
    Objects.requireNonNull(transferDto);
    transferService.updateTransfer(transferId, transferDto);
    return Response.ok().build();
  }

  @Operation(summary = "Delete transfer", description = "Deletes an existing transfer")
  @APIResponse(responseCode = "204", description = "Transfer deleted successfully")
  @APIResponse(responseCode = "404", description = "Transfer not found")
  @DELETE
  @Path("/{transferId}")
  public Response deleteTransfer(@PathParam("transferId") String transferId)
  {
    transferService.deleteTransfer(transferId);
    return Response.noContent().build();
  }
}
