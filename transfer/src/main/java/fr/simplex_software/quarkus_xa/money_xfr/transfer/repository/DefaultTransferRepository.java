package fr.simplex_software.quarkus_xa.money_xfr.transfer.repository;

import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;

import java.util.*;

@ApplicationScoped
@Alternative
@Default
public class DefaultTransferRepository implements TransferRepository
{
  private final Map<String, Transfer> transfers = new HashMap<>();

  @Override
  public void createTransfer(Transfer transfer)
  {
    transfers.put(transfer.getTransferId(), transfer);
  }

  @Override
  public Optional<Transfer> getTransfer(String transferId)
  {
    return Optional.ofNullable(transfers.get(transferId));
  }

  @Override
  public List<Transfer> getTransfers()
  {
    return new ArrayList<>(transfers.values());
  }

  @Override
  public void updateTransfer(Transfer transfer)
  {
    transfers.put(transfer.getTransferId(), transfer);
  }

  @Override
  public void deleteTransfer(String transferId)
  {
    transfers.remove(transferId);
  }
}
