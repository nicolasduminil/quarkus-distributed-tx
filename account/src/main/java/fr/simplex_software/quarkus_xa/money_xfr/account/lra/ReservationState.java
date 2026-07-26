package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

public enum ReservationState
{
  PENDING,    // Held, not yet applied to the balance
  CONFIRMED,  // Applied to the balance by @Complete
  RELEASED    // Dropped by @Compensate; the balance never moved
}
