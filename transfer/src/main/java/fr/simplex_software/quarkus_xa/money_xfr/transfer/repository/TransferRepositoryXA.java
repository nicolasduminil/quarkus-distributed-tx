package fr.simplex_software.quarkus_xa.money_xfr.transfer.repository;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;
import io.quarkus.hibernate.orm.panache.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;

import java.util.*;

@ApplicationScoped
@Alternative
public class TransferRepositoryXA implements PanacheRepositoryBase<Transfer, String>, TransferRepository
{
  @Override
  public void createTransfer(Transfer transfer)
  {
    persist(transfer);
  }

  @Override
  public Optional<Transfer> getTransfer(String transferId)
  {
    return findByIdOptional(transferId);
  }

  @Override
  public List<Transfer> getTransfers()
  {
    return listAll();
  }

  @Override
  public void updateTransfer(Transfer transfer)
  {
    update("sourceAccountId = ?1, destinationAccountId = ?2, amount = ?3, "
        + "currency = ?4, status = ?5, timestamp = ?6 where transferId = ?7",
      transfer.getSourceAccountId(), transfer.getDestinationAccountId(), transfer.getAmount(),
      transfer.getCurrency(), transfer.getStatus(), transfer.getTimestamp(), transfer.getTransferId());
  }

  @Override
  public void deleteTransfer(String transferId)
  {
    deleteById(transferId);
  }
}
