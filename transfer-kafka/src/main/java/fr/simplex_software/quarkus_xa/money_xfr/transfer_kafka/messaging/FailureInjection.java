package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging;

import jakarta.enterprise.context.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@ApplicationScoped
public class FailureInjection
{
  private final Map<String, AtomicInteger> remaining = new ConcurrentHashMap<>();

  public void armOnce(String requestId)
  {
    remaining.put(requestId, new AtomicInteger(1));
  }

  public boolean consume(String requestId)
  {
    AtomicInteger counter = remaining.get(requestId);
    return counter != null && counter.compareAndSet(1, 0);
  }
}
