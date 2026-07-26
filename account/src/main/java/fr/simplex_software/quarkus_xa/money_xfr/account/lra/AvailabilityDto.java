package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import java.math.*;

/**
 * Exposes the gap between what an account holds and what it can spend. The XA
 * stack has no equivalent: under 2PC there is no observable in-between state.
 */
public class AvailabilityDto
{
  private String accountId;
  private BigDecimal balance;
  private BigDecimal held;
  private BigDecimal available;

  public AvailabilityDto()
  {
  }

  public AvailabilityDto(String accountId, BigDecimal balance, BigDecimal held, BigDecimal available)
  {
    this.accountId = accountId;
    this.balance = balance;
    this.held = held;
    this.available = available;
  }

  public String getAccountId()
  {
    return accountId;
  }

  public void setAccountId(String accountId)
  {
    this.accountId = accountId;
  }

  public BigDecimal getBalance()
  {
    return balance;
  }

  public void setBalance(BigDecimal balance)
  {
    this.balance = balance;
  }

  public BigDecimal getHeld()
  {
    return held;
  }

  public void setHeld(BigDecimal held)
  {
    this.held = held;
  }

  public BigDecimal getAvailable()
  {
    return available;
  }

  public void setAvailable(BigDecimal available)
  {
    this.available = available;
  }
}
