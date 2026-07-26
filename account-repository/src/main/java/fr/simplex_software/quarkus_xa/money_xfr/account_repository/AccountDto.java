package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import java.math.*;

public class AccountDto
{
  private String accountId;
  private String customerName;
  private BigDecimal balance;
  private String currency;

  public AccountDto()
  {
  }

  public AccountDto(String accountId, String customerName, BigDecimal balance, String currency)
  {
    this.accountId = accountId;
    this.customerName = customerName;
    this.balance = balance;
    this.currency = currency;
  }

  public String getAccountId()
  {
    return accountId;
  }

  public void setAccountId(String accountId)
  {
    this.accountId = accountId;
  }

  public String getCustomerName()
  {
    return customerName;
  }

  public void setCustomerName(String customerName)
  {
    this.customerName = customerName;
  }

  public BigDecimal getBalance()
  {
    return balance;
  }

  public void setBalance(BigDecimal balance)
  {
    this.balance = balance;
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
