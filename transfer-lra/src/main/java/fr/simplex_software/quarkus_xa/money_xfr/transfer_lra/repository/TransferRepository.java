package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.repository;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.domain.*;
import io.quarkus.hibernate.orm.panache.*;
import jakarta.enterprise.context.*;

import java.util.*;

/**
 * Single Panache implementation — unlike the XA module there is no in-memory
 * alternative here, because a saga participant has to survive a restart to answer
 * the coordinator's retries.
 */
@ApplicationScoped
public class TransferRepository implements PanacheRepositoryBase<Transfer, String>
{
  public Optional<Transfer> findByTransferId(String transferId)
  {
    return findByIdOptional(transferId);
  }

  public Optional<Transfer> findByLra(String lraId)
  {
    return find("lraId", lraId).firstResultOptional();
  }

  public List<Transfer> findAllTransfers()
  {
    return listAll();
  }

  public long countByStatus(TransferStatus status)
  {
    return count("status", status);
  }
}
