package fr.simplex_software.quarkus_xa.money_xfr.transfer.domain;

public enum TransferStatus
{
  PENDING,      // Initial state when transaction is created
  COMPLETED,    // Successfully completed transaction
  FAILED,       // TransferDto failed during processing
  CANCELLED,    // TransferDto cancelled by user or system
  REJECTED,     // TransferDto rejected due to validation/business rules
  PROCESSING    // TransferDto is being processed
}
