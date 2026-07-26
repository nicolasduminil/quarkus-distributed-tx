package fr.simplex_software.quarkus_xa.money_xfr.transfer.repository;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;

import java.util.*;

public interface TransferRepository
{
  void createTransfer(Transfer transfer);
  Optional<Transfer> getTransfer(String transferId);
  List<Transfer> getTransfers();
  void updateTransfer(Transfer transfer);
  void deleteTransfer(String transferId);
}
