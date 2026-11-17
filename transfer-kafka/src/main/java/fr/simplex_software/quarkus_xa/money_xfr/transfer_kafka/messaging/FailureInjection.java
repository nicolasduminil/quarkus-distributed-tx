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
    remaining.computeIfAbsent(requestId, key -> new AtomicInteger()).set(1);
  }

  public boolean consume(String requestId)
  {
    AtomicInteger counter = remaining.get(requestId);
    return counter != null && counter.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
  }
}
