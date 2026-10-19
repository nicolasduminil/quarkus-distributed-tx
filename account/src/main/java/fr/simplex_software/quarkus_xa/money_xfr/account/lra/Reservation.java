package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import jakarta.persistence.*;

import java.math.*;

@Entity
@Table(name = "RESERVATIONS",
  uniqueConstraints = @UniqueConstraint(name = "UK_RESERVATION_LRA",
    columnNames = {"LRA_ID", "ACCOUNT_ID", "RESERVATION_TYPE"}))
public class Reservation
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "RESERVATION_ID")
  private Long id;
  @Column(name = "LRA_ID", nullable = false, length = 255)
  private String lraId;
  @Column(name = "ACCOUNT_ID", nullable = false, length = 20)
  private String accountId;
  @Column(name = "RESERVATION_AMOUNT", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;
  @Enumerated(EnumType.STRING)
  @Column(name = "RESERVATION_TYPE", nullable = false, length = 10)
  private ReservationType type;
  @Enumerated(EnumType.STRING)
  @Column(name = "RESERVATION_STATE", nullable = false, length = 10)
  private ReservationState state;

  public Reservation()
  {
  }

  public Reservation(String lraId, String accountId, BigDecimal amount, ReservationType type)
  {
    this.lraId = lraId;
    this.accountId = accountId;
    this.amount = amount;
    this.type = type;
    this.state = ReservationState.PENDING;
  }

   public BigDecimal signedAmount()
  {
    return type == ReservationType.DEBIT ? amount.negate() : amount;
  }

  public Long getId()
  {
    return id;
  }

  public String getLraId()
  {
    return lraId;
  }

  public String getAccountId()
  {
    return accountId;
  }

  public BigDecimal getAmount()
  {
    return amount;
  }

  public ReservationType getType()
  {
    return type;
  }

  public ReservationState getState()
  {
    return state;
  }

  public void setState(ReservationState state)
  {
    this.state = state;
  }
}
