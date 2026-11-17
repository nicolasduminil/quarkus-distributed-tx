package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging;

import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain.*;
import jakarta.enterprise.context.*;
import org.eclipse.microprofile.reactive.messaging.*;

import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class TransferEventObserver
{
  private static final int WINDOW = 200;

  private final Deque<TransferEvent> received = new ConcurrentLinkedDeque<>();

  @Incoming("transfer-events-in")
  public void onTransferEvent(TransferEvent event)
  {
    received.addLast(event);
    while (received.size() > WINDOW)
      received.pollFirst();
  }

  public List<TransferEvent> received()
  {
    return List.copyOf(received);
  }
}
