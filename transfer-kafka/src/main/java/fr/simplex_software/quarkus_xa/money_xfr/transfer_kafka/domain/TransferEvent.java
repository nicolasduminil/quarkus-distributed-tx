package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain;

import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;

import java.math.*;
import java.time.*;

public record TransferEvent(String requestId, String transferId, String sourceAccountId,
                            String destinationAccountId, BigDecimal amount, String currency,
                            TransferStatus status, String reason, LocalDateTime timestamp)
{
  public static TransferEvent of(String requestId, TransferDto transfer)
  {
    return of(requestId, transfer, "");
  }

  public static TransferEvent of(String requestId, TransferDto transfer, String reason)
  {
    return new TransferEvent(requestId, transfer.getTransferId(), transfer.getSourceAccountId(),
      transfer.getDestinationAccountId(), transfer.getAmount(), transfer.getCurrency(),
      transfer.getStatus(), reason, transfer.getTimestamp());
  }
}
