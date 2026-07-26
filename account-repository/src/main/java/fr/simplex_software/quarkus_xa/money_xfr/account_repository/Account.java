package fr.simplex_software.quarkus_xa.money_xfr.account_repository;

import jakarta.persistence.*;

import java.math.*;

@Entity
@Table(name = "ACCOUNTS")
public class Account
{
  @Id
  @Column(name = "ACCOUNT_ID", length = 20, nullable = false, updatable = false)
  private String id;
  @Column(name = "ACCOUNT_OWNER", length = 20, nullable = false)
  private String holder;
  @Column(name = "ACCOUNT_BALANCE", nullable = false, precision = 19, scale = 2)
  private BigDecimal balance;
  @Column(name = "ACCOUNT_CURRENCY", nullable = false, length = 3)
  private String currency;

  public Account()
  {
  }

  public Account(String id, String holder, BigDecimal balance, String currency)
  {
    this.id = id;
    this.holder = holder;
    this.balance = balance;
    this.currency = currency;
  }

  public Account(AccountDto accountDto)
  {
    this(accountDto.getAccountId(), accountDto.getCustomerName(), accountDto.getBalance(), accountDto.getCurrency());
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id;
  }

  public String getHolder()
  {
    return holder;
  }

  public void setHolder(String holder)
  {
    this.holder = holder;
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

  public void debit(BigDecimal amount)
  {
    this.balance = this.balance.subtract(amount);
  }

  public void credit(BigDecimal amount)
  {
    this.balance = this.balance.add(amount);
  }

  public AccountDto toDto()
  {
    return new AccountDto(id, holder, balance, currency);
  }
}
