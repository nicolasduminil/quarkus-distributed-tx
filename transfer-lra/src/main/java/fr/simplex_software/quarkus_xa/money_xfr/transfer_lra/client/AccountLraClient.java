package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.rest.client.inject.*;

import java.math.*;

/**
 * Calls the account service's saga endpoints.
 * <p>
 * Nothing here mentions the LRA context: Narayana's {@code ClientLRARequestFilter}
 * is a {@code @Provider} that Quarkus discovers automatically and it injects the
 * {@code Long-Running-Action} header on every outgoing call made inside an active
 * LRA. That is what makes the remote account service a participant in <em>this</em>
 * saga without any plumbing at the call site.
 */
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
