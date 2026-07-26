package fr.simplex_software.quarkus_xa.money_xfr.common.domain;

import org.eclipse.microprofile.openapi.annotations.media.*;

import java.math.*;

public class TransferRequest
{
  @Schema(
    description = "Source account ID",
    example = "ACC123",
    required = true
  )
  private String sourceAccountId;
  @Schema(
    description = "Destination account ID",
    example = "ACC123",
    required = true
  )
  private String destinationAccountId;
  @Schema(
    description = "Amount to transfer",
    example = "100.50",
    required = true,
    minimum = "0.01"
  )
  private BigDecimal amount;
  @Schema(
    description = "TransferDto currency",
    example = "EUR",
    required = true
  )
  private String currency;

  public TransferRequest()
  {
  }

  public TransferRequest(String sourceAccountId, String destinationAccountId, BigDecimal amount, String currency)
  {
    this.sourceAccountId = sourceAccountId;
    this.destinationAccountId = destinationAccountId;
    this.amount = amount;
    this.currency = currency;
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
}
