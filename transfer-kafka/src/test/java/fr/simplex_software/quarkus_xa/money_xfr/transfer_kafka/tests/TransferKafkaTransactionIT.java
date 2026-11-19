package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.tests;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging.*;
import io.quarkus.narayana.jta.*;
import io.quarkus.test.junit.*;
import io.smallrye.reactive.messaging.kafka.api.*;
import jakarta.inject.*;
import org.eclipse.microprofile.reactive.messaging.*;
import org.junit.jupiter.api.*;

import java.math.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransferKafkaTransactionIT
{
  private static final String SOURCE = "ACC123";
  private static final String DESTINATION = "ACC321";
  private static final BigDecimal AMOUNT = new BigDecimal("12.34");
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Inject
  AccountRepository accountRepository;
  @Inject
  TransferEventObserver observer;
  @Inject
  TransferRequestListener listener;
  @Inject
  FailureInjection failureInjection;
  @Inject
  @Channel("transfer-requests-out")
  Emitter<TransferRequest> requests;

  private BigDecimal balanceOf(String accountId)
  {
    return QuarkusTransaction.requiringNew()
      .call(() -> accountRepository.getAccount(accountId).orElseThrow().getBalance());
  }

  private void publish(String requestId, BigDecimal amount)
  {
    requests.send(Message.of(new TransferRequest(SOURCE, DESTINATION, amount, "EUR"))
      .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withKey(requestId).build()));
  }

  private List<TransferEvent> eventsOf(String requestId)
  {
    return observer.received().stream().filter(event -> requestId.equals(event.requestId())).toList();
  }

  @Test
  @Order(10)
  public void listener_validRequest_movesTheMoneyAndPublishesOneEvent()
  {
    String requestId = "REQ-COMMIT";
    BigDecimal sourceBefore = balanceOf(SOURCE);
    BigDecimal destinationBefore = balanceOf(DESTINATION);

    publish(requestId, AMOUNT);

    await().atMost(TIMEOUT).untilAsserted(() ->
    {
      assertThat(listener.deliveriesOf(requestId)).isEqualTo(1);
      assertThat(balanceOf(SOURCE)).isEqualByComparingTo(sourceBefore.subtract(AMOUNT));
      assertThat(balanceOf(DESTINATION)).isEqualByComparingTo(destinationBefore.add(AMOUNT));
      assertThat(eventsOf(requestId)).singleElement()
        .extracting(TransferEvent::status).isEqualTo(TransferStatus.COMPLETED);
    });
  }

  @Test
  @Order(20)
  public void listener_failureBetweenTheCommits_rewindsBothResourcesAndTheBrokerRedelivers()
  {
    String requestId = "REQ-ROLLBACK";
    BigDecimal sourceBefore = balanceOf(SOURCE);
    BigDecimal destinationBefore = balanceOf(DESTINATION);
    failureInjection.armOnce(requestId);

    publish(requestId, AMOUNT);

    await().atMost(TIMEOUT)
      .untilAsserted(() -> assertThat(listener.deliveriesOf(requestId)).isEqualTo(2));
    await().atMost(TIMEOUT).untilAsserted(() ->
    {
      assertThat(balanceOf(SOURCE)).isEqualByComparingTo(sourceBefore.subtract(AMOUNT));
      assertThat(balanceOf(DESTINATION)).isEqualByComparingTo(destinationBefore.add(AMOUNT));
      assertThat(eventsOf(requestId)).singleElement()
        .extracting(TransferEvent::status).isEqualTo(TransferStatus.COMPLETED);
    });
  }

  @Test
  @Order(30)
  public void listener_insufficientFunds_commitsARejectionAndLeavesTheBalances()
  {
    String requestId = "REQ-REJECTED";
    BigDecimal sourceBefore = balanceOf(SOURCE);
    BigDecimal destinationBefore = balanceOf(DESTINATION);

    publish(requestId, sourceBefore.add(new BigDecimal("1000000.00")));

    await().atMost(TIMEOUT).untilAsserted(() ->
    {
      assertThat(eventsOf(requestId)).singleElement()
        .extracting(TransferEvent::status).isEqualTo(TransferStatus.REJECTED);
      assertThat(eventsOf(requestId).getFirst().reason()).contains("###");
      assertThat(balanceOf(SOURCE)).isEqualByComparingTo(sourceBefore);
      assertThat(balanceOf(DESTINATION)).isEqualByComparingTo(destinationBefore);
    });
    assertThat(listener.deliveriesOf(requestId)).isEqualTo(1);
  }

  @Test
  @Order(40)
  public void listener_requestAlreadyApplied_movesTheMoneyOnlyOnce()
  {
    String requestId = "REQ-REPLAYED";
    BigDecimal sourceBefore = balanceOf(SOURCE);

    publish(requestId, AMOUNT);
    await().atMost(TIMEOUT).untilAsserted(() ->
      assertThat(balanceOf(SOURCE)).isEqualByComparingTo(sourceBefore.subtract(AMOUNT)));

    publish(requestId, AMOUNT);
    await().atMost(TIMEOUT)
      .untilAsserted(() -> assertThat(listener.deliveriesOf(requestId)).isEqualTo(2));
    assertThat(balanceOf(SOURCE)).isEqualByComparingTo(sourceBefore.subtract(AMOUNT));
    assertThat(eventsOf(requestId)).hasSize(1);
  }
}
