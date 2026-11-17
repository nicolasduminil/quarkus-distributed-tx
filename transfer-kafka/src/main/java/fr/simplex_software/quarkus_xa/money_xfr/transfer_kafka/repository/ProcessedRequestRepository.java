package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.repository;

import fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain.*;
import io.quarkus.hibernate.orm.panache.*;
import jakarta.enterprise.context.*;

@ApplicationScoped
public class ProcessedRequestRepository implements PanacheRepositoryBase<ProcessedRequest, String>
{
  public boolean alreadyProcessed(String requestId)
  {
    return findByIdOptional(requestId).isPresent();
  }

  public void record(ProcessedRequest processedRequest)
  {
    persist(processedRequest);
  }
}
