package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import jakarta.persistence.*;

import java.math.*;

/**
 * A hold placed on an account for the duration of a saga.
 * <p>
 * This is what makes compensation correct rather than merely plausible. A naive
 * saga debits immediately and credits the money back if anything fails; that is
 * wrong twice over — the balance visibly dips and recovers (a dirty read for any
 * concurrent observer), and the compensating credit can itself fail, leaving the
 * saga stuck. Here the balance is never touched until {@code @Complete}: a debit
 * only records a PENDING reservation, and compensation simply releases it, so
 * there is literally nothing to undo.
 */
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

  /** The signed effect this reservation will have once confirmed. */
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
