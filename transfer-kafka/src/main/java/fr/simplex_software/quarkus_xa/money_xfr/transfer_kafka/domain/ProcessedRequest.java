package fr.simplex_software.quarkus_xa.money_xfr.transfer_kafka.domain;

import jakarta.persistence.*;

import java.time.*;

@Entity
@Table(name = "PROCESSED_REQUESTS")
public class ProcessedRequest
{
  @Id
  @Column(name = "REQUEST_ID", length = 64, nullable = false, updatable = false)
  private String requestId;
  @Column(name = "TRANSFER_ID", length = 20, nullable = false)
  private String transferId;
  @Column(name = "PROCESSED_AT", nullable = false)
  private LocalDateTime processedAt;

  public ProcessedRequest()
  {
  }

  public ProcessedRequest(String requestId, String transferId, LocalDateTime processedAt)
  {
    this.requestId = requestId;
    this.transferId = transferId;
    this.processedAt = processedAt;
  }

  public String getRequestId()
  {
    return requestId;
  }

  public void setRequestId(String requestId)
  {
    this.requestId = requestId;
  }

  public String getTransferId()
  {
    return transferId;
  }

  public void setTransferId(String transferId)
  {
    this.transferId = transferId;
  }

  public LocalDateTime getProcessedAt()
  {
    return processedAt;
  }

  public void setProcessedAt(LocalDateTime processedAt)
  {
    this.processedAt = processedAt;
  }
}
