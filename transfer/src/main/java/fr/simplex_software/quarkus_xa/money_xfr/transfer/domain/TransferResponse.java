package fr.simplex_software.quarkus_xa.money_xfr.transfer.domain;

import java.time.*;

public class TransferResponse
{
  private String transferId;
  private TransferStatus status;
  private LocalDateTime timestamp;

  public TransferResponse()
  {
  }

  public TransferResponse(String transferId, TransferStatus status, LocalDateTime timestamp)
  {
    this.transferId = transferId;
    this.status = status;
    this.timestamp = timestamp;
  }

  public String getTransferId()
  {
    return transferId;
  }

  public void setTransferId(String transferId)
  {
    this.transferId = transferId;
  }

  public TransferStatus getStatus()
  {
    return status;
  }

  public void setStatus(TransferStatus status)
  {
    this.status = status;
  }

  public LocalDateTime getTimestamp()
  {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp)
  {
    this.timestamp = timestamp;
  }

  public static TransferResponse fromTransfer(TransferDto transfer)
  {
    TransferResponse response = new TransferResponse();
    response.setTransferId(transfer.getTransferId());
    response.setStatus(transfer.getStatus());
    response.setTimestamp(transfer.getTimestamp());
    return response;
  }
}
