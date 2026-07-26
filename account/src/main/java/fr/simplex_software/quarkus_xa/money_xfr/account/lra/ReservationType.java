package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

public enum ReservationType
{
  DEBIT,   // Money to be taken out of the account when the saga closes
  CREDIT   // Money to be paid into the account when the saga closes
}
