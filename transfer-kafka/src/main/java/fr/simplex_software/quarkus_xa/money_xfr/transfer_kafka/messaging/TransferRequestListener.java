package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging;

import fr.simplex_software.quarkus_xa.money_xfr.common.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_core.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_core.repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_core.service.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.repository.*;
import io.quarkus.narayana.jta.*;
import io.smallrye.common.annotation.*;
import io.smallrye.mutiny.*;
import io.smallrye.reactive.messaging.kafka.api.*;
import io.smallrye.reactive.messaging.kafka.transactions.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import org.eclipse.microprofile.reactive.messaging.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@ApplicationScoped
public class TransferRequestListener
{
  @Inject
  TransferService transferService;
  @Inject
  TransferRepository transferRepository;
  @Inject
  ProcessedRequestRepository processedRequestRepository;
  @Inject
  FailureInjection failureInjection;
  @Channel("transfer-events")
  KafkaTransactions<TransferEvent> emitter;

  // Counted per request so a test can prove that an aborted transaction brought the
  // same message back. Reached from the Kafka worker threads.
  private final Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();

  public Map<String, Integer> deliveries()
  {
    Map<String, Integer> snapshot = new HashMap<>();
    deliveries.forEach((requestId, count) -> snapshot.put(requestId, count.get()));
    return snapshot;
  }

  public int deliveriesOf(String requestId)
  {
    AtomicInteger count = deliveries.get(requestId);
    return count == null ? 0 : count.get();
  }

  @Incoming("transfer-requests")
  @Blocking
  public Uni<Void> onTransferRequest(Message<TransferRequest> message)
  {
    String requestId = requestIdOf(message);
    deliveries.computeIfAbsent(requestId, key -> new AtomicInteger()).incrementAndGet();
    return emitter.withTransactionAndAck(message, tx ->
    {
      try
      {
        apply(requestId, message.getPayload()).ifPresent(tx::send);
        return Uni.createFrom().voidItem();
      }
      catch (RuntimeException runtimeException)
      {
        // Returning the failure rather than letting it escape keeps the abort inside
        // Mutiny, where withTransactionAndAck nacks the message and the offset stays
        // uncommitted, so the broker delivers the record again.
        return Uni.createFrom().failure(runtimeException);
      }
    });
  }

  private Optional<TransferEvent> apply(String requestId, TransferRequest request)
  {
    if (QuarkusTransaction.requiringNew()
      .call(() -> processedRequestRepository.alreadyProcessed(requestId)))
      return Optional.empty();
    try
    {
      return Optional.of(complete(requestId, request));
    }
    catch (ApplicationException rejection)
    {
      // A business rejection is an outcome rather than a transaction failure, so it
      // commits and allows the offset to advance. Aborting here would return a
      // message whose next delivery would be refused for the same reason.
      return Optional.of(reject(requestId, request, rejection.getMessage()));
    }
  }

  private TransferEvent complete(String requestId, TransferRequest request)
  {
    return QuarkusTransaction.requiringNew().call(() ->
    {
      TransferDto transfer = transferService.processTransfer(request);
      processedRequestRepository.record(
        new ProcessedRequest(requestId, transfer.getTransferId(), LocalDateTime.now()));
      if (failureInjection.consume(requestId))
        throw new IllegalStateException(
          "### injected failure between the database write and the Kafka commit");
      return TransferEvent.of(requestId, transfer);
    });
  }

  private TransferEvent reject(String requestId, TransferRequest request, String reason)
  {
    return QuarkusTransaction.requiringNew().call(() ->
    {
      Transfer rejected = new Transfer(Common.getRandomId(), request.getSourceAccountId(),
        request.getDestinationAccountId(), request.getAmount(), request.getCurrency(),
        TransferStatus.REJECTED, LocalDateTime.now());
      transferRepository.createTransfer(rejected);
      processedRequestRepository.record(
        new ProcessedRequest(requestId, rejected.getTransferId(), LocalDateTime.now()));
      return TransferEvent.of(requestId, rejected.toDto(), reason);
    });
  }

  private String requestIdOf(Message<TransferRequest> message)
  {
    return message.getMetadata(IncomingKafkaRecordMetadata.class)
      .map(metadata -> String.valueOf(metadata.getKey()))
      .orElseGet(Common::getRandomId);
  }
}
