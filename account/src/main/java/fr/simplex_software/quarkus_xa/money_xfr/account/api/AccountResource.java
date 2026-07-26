package fr.simplex_software.quarkus_xa.money_xfr.account.api;

import fr.simplex_software.quarkus_xa.money_xfr.account.service.*;
import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.media.*;
import org.eclipse.microprofile.openapi.annotations.responses.*;
import org.eclipse.microprofile.openapi.annotations.tags.*;

import java.math.*;
import java.util.*;

@ApplicationScoped
@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Account Management", description = "Operations for managing bank accounts")
public class AccountResource
{
  @Inject
  AccountService accountService;

  @Operation(summary = "Get account details", description = "Retrieves account information for the specified account ID")
  @APIResponse(responseCode = "200", description = "Account found successfully",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccountDto.class)))
  @APIResponse(responseCode = "404", description = "Account not found")
  @GET
  @Path("/{accountId}")
  public Response getAccount(@PathParam("accountId") String accountId)
  {
    return Response.ok(accountService.getAccount(accountId)).build();
  }

  @Operation(summary = "Get accounts details", description = "Retrieves the list of the existing accounts")
  @APIResponse(responseCode = "200", description = "Success",
    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccountDto.class)))
  @GET
  public Response getAccounts()
  {
    return Response.ok(accountService.getAccounts()).build();
  }

  @Operation(summary = "Create new account", description = "Creates a new bank account")
  @APIResponse(responseCode = "201", description = "Account created successfully")
  @POST
  public Response createAccount(AccountDto accountDto)
  {
    Objects.requireNonNull(accountDto);
    accountService.createAccount(accountDto);
    return Response.status(Response.Status.CREATED).build();
  }

  @Operation(summary = "Update account", description = "Updates an existing account's information")
  @APIResponse(responseCode = "200", description = "Account updated successfully")
  @APIResponse(responseCode = "404", description = "Account not found")
  @PUT
  @Path("/{accountId}")
  public Response updateAccount(@PathParam("accountId") String accountId, AccountDto accountDto)
  {
    Objects.requireNonNull(accountDto);
    accountService.updateAccount(accountId, accountDto);
    return Response.ok().build();
  }

  @Operation(summary = "Delete account", description = "Deletes an existing account")
  @APIResponse(responseCode = "204", description = "Account deleted successfully")
  @APIResponse(responseCode = "404", description = "Account not found")
  @DELETE
  @Path("/{accountId}")
  public Response deleteAccount(@PathParam("accountId") String accountId)
  {
    accountService.deleteAccount(accountId);
    return Response.noContent().build();
  }

  @Operation(summary = "Debit account", description = "Withdraws money from the specified account")
  @APIResponse(responseCode = "200", description = "Amount debited successfully")
  @APIResponse(responseCode = "400", description = "Invalid or insufficient amount")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  @Path("/{accountId}/debit")
  public Response debit(@PathParam("accountId") String accountId, BigDecimal amount)
  {
    Objects.requireNonNull(accountId);
    Objects.requireNonNull(amount);
    if (amount.compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidAmountException(accountId);
    accountService.debit(accountId, amount);
    return Response.ok().build();
  }

  @Operation(summary = "Credit account", description = "Deposits money into the specified account")
  @APIResponse(responseCode = "200", description = "Amount credited successfully")
  @APIResponse(responseCode = "400", description = "Invalid credit amount")
  @APIResponse(responseCode = "404", description = "Account not found")
  @POST
  @Path("/{accountId}/credit")
  public Response credit(@PathParam("accountId") String accountId, BigDecimal amount)
  {
    Objects.requireNonNull(accountId);
    Objects.requireNonNull(amount);
    if (amount.compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidAmountException(accountId);
    accountService.credit(accountId, amount);
    return Response.ok().build();
  }
}
