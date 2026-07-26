package fr.simplex_software.quarkus_xa.money_xfr.transfer.domain;

import jakarta.persistence.*;

import java.math.*;
import java.time.*;

@Entity
@Table(name = "XFERS")
public class Transfer
{
  @Id
  @Column(name = "TRANSFER_ID", length = 20, nullable = false, updatable = false)
  private String transferId;
  @Column(name = "SOURCE_ACCOUNT_ID", length = 20, nullable = false)
  private String sourceAccountId;
  @Column(name = "DESTINATION_ACCOUNT_ID", length = 20, nullable = false)
  private String destinationAccountId;
  @Column(name="TRANSFER_AMOUNT", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;
  @Column(name="TRANSFER_CURRENCY", nullable = false, length = 3)
  private String currency;
  @Enumerated(EnumType.STRING)
  @Column(name = "TRANSFER_STATUS", nullable = false, length = 20)
  private TransferStatus status;
  @Column(name = "TRANSFER_DATE_TIME", nullable = false)
  private LocalDateTime timestamp;

  public Transfer()
  {
  }

  public Transfer(String transferId, String sourceAccountId, String destinationAccountId, BigDecimal amount, String currency, TransferStatus status, LocalDateTime timestamp)
  {
    this.transferId = transferId;
    this.sourceAccountId = sourceAccountId;
    this.destinationAccountId = destinationAccountId;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.timestamp = timestamp;
  }

  public Transfer(TransferDto transferDto)
  {
    this (transferDto.getTransferId(), transferDto.getSourceAccountId(), transferDto.getDestinationAccountId(),
      transferDto.getAmount(), transferDto.getCurrency(), transferDto.getStatus(), transferDto.getTimestamp());
  }

  public TransferDto toDto()
  {
    return new TransferDto(transferId, sourceAccountId, destinationAccountId, amount,
      currency, status, timestamp);
  }

  public String getTransferId()
  {
    return transferId;
  }

  public void setTransferId(String transferId)
  {
    this.transferId = transferId;
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
