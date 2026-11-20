package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.api;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging.*;
import io.smallrye.reactive.messaging.kafka.api.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.*;
import org.eclipse.microprofile.openapi.annotations.tags.*;
import org.eclipse.microprofile.reactive.messaging.*;

import java.util.*;

@ApplicationScoped
@Path("/kafka")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Kafka Transaction Demonstration",
  description = "Drives the listener and exposes what a read_committed reader observed")
public class TransferKafkaResource
{
  @Inject
  TransferRequestListener listener;
  @Inject
  TransferEventObserver observer;
  @Inject
  FailureInjection failureInjection;
  @Inject
  AccountRepository accountRepository;
  @Channel("transfer-requests-out")
  Emitter<TransferRequest> requests;

  @Operation(summary = "Publish a transfer request",
    description = "Puts a transfer request on the transfer-requests topic for the listener")
  @POST
  @Path("/requests")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response publish(@QueryParam("requestId") String requestId, TransferRequest request)
  {
    Objects.requireNonNull(request);
    String key = requestId == null || requestId.isBlank() ? Common.getRandomId() : requestId;
    requests.send(Message.of(request).addMetadata(
      OutgoingKafkaRecordMetadata.<String>builder().withKey(key).build()));
    return Response.accepted(Map.of("requestId", key)).build();
  }

  @Operation(summary = "Read the observed events",
    description = "What a consumer with isolation.level=read_committed has received")
  @GET
  @Path("/events")
  public Response events()
  {
    return Response.ok(observer.received()).build();
  }

  @Operation(summary = "Read an account balance",
    description = "The balance in the accounts database, as the listener left it")
  @GET
  @Path("/accounts/{accountId}")
  public Response account(@PathParam("accountId") String accountId)
  {
    return Response.ok(accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId)).toDto()).build();
  }

  @Operation(summary = "Read the delivery counts",
    description = "How many times each request has been delivered to the listener")
  @GET
  @Path("/deliveries")
  public Response deliveries()
  {
    return Response.ok(listener.deliveries()).build();
  }

  @Operation(summary = "Arm a single failure",
    description = "The next delivery of this request fails between the two commits")
  @POST
  @Path("/fail-once/{requestId}")
  public Response failOnce(@PathParam("requestId") String requestId)
  {
    failureInjection.armOnce(requestId);
    return Response.noContent().build();
  }

  @Operation(summary = "Arm a halt in the residual window",
    description = "The next delivery of this request terminates the process after the "
      + "database commit and before the Kafka commit")
  @POST
  @Path("/halt-once/{requestId}")
  public Response haltOnce(@PathParam("requestId") String requestId)
  {
    failureInjection.armHaltOnce(requestId);
    return Response.noContent().build();
  }
}
