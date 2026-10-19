package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.rest.client.inject.*;

import java.math.*;

@RegisterRestClient(configKey = "account-lra")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/lra/accounts")
public interface AccountLraClient
{
  @POST
  @Path("/{accountId}/debit")
  void reserveDebit(@PathParam("accountId") String accountId, BigDecimal amount);

  @POST
  @Path("/{accountId}/credit")
  void reserveCredit(@PathParam("accountId") String accountId, BigDecimal amount);
}
