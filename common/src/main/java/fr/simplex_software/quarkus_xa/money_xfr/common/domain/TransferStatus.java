package fr.simplex_software.quarkus_xa.money_xfr.common.domain;

public enum TransferStatus
{
  PENDING,      // Initial state when transaction is created
  COMPLETED,    // Successfully completed transaction
  FAILED,       // TransferDto failed during processing
  CANCELLED,    // TransferDto cancelled by user or system
  REJECTED,     // TransferDto rejected due to validation/business rules
  PROCESSING,   // TransferDto is being processed
  // Saga-only states. The XA implementation never reaches these: a 2PC rollback
  // leaves no trace, whereas a saga has to record that it undid itself.
  COMPENSATING, // The LRA is being cancelled; participants are compensating
  COMPENSATED   // Every participant compensated; the transfer was undone
}
