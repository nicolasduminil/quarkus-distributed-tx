package fr.simplex_software.quarkus_xa.money_xfr.transfer.domain;

import java.math.*;
import java.time.*;

public class TransferDto
{
  private String transferId;
  private String sourceAccountId;
  private String destinationAccountId;
  private BigDecimal amount;
  private String currency;
  private TransferStatus status;
  private LocalDateTime timestamp;

  public TransferDto()
  {
  }

  public TransferDto(String transferId, String sourceAccountId, String destinationAccountId, BigDecimal amount, String currency, TransferStatus status, LocalDateTime timestamp)
  {
    this.transferId = transferId;
    this.sourceAccountId = sourceAccountId;
    this.destinationAccountId = destinationAccountId;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.timestamp = timestamp;
  }

  public String getTransferId()
  {
    return transferId;
  }

  public void setTransferId(String transactionId)
  {
    this.transferId = transactionId;
  }

  public String getSourceAccountId()
  {
    return sourceAccountId;
  }

  public void setSourceAccountId(String sourceAccountId)
  {
    this.sourceAccountId = sourceAccountId;
  }

  public String getDestinationAccountId()
  {
    return destinationAccountId;
  }

  public void setDestinationAccountId(String destinationAccountId)
  {
    this.destinationAccountId = destinationAccountId;
  }

  public BigDecimal getAmount()
  {
    return amount;
  }

  public void setAmount(BigDecimal amount)
  {
    this.amount = amount;
  }

  public String getCurrency()
  {
    return currency;
  }

  public void setCurrency(String currency)
  {
    this.currency = currency;
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
}
