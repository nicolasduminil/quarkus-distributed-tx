package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.messaging;

import jakarta.enterprise.context.*;

import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class FailureInjection
{
  private final Set<String> failures = ConcurrentHashMap.newKeySet();
  private final Set<String> halts = ConcurrentHashMap.newKeySet();

  public void armOnce(String requestId)
  {
    failures.add(requestId);
  }

  public boolean consume(String requestId)
  {
    return failures.remove(requestId);
  }

  public void armHaltOnce(String requestId)
  {
    halts.add(requestId);
  }

  public boolean consumeHalt(String requestId)
  {
    return halts.remove(requestId);
  }
}
