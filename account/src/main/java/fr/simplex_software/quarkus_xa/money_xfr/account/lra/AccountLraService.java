package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import org.eclipse.microprofile.lra.annotation.*;

import java.math.*;
import java.util.*;

/**
 * The saga participant logic for accounts, using reserve-then-confirm.
 * <p>
 * Every method here is <b>idempotent</b>, which is not optional: an LRA
 * coordinator retries {@code @Complete} and {@code @Compensate} until it gets an
 * answer, so a participant that applies its effect twice corrupts the balance.
 * Idempotency is enforced by keying reservations on the LRA id and by treating
 * any non-PENDING reservation as already handled.
 */
@ApplicationScoped
public class AccountLraService
{
  @Inject
  AccountRepository accountRepository;
  @Inject
  ReservationRepository reservations;

  @Transactional
  public void reserveDebit(String lraId, String accountId, BigDecimal amount)
  {
    Account account = requireAccount(accountId);
    requirePositive(accountId, amount);
    if (reservations.find(lraId, accountId, ReservationType.DEBIT).isPresent())
      return;
    if (available(account).compareTo(amount) < 0)
      throw new InsufficientFundsException(accountId);
    reservations.persist(new Reservation(lraId, accountId, amount, ReservationType.DEBIT));
  }

  @Transactional
  public void reserveCredit(String lraId, String accountId, BigDecimal amount)
  {
    requireAccount(accountId);
    requirePositive(accountId, amount);
    if (reservations.find(lraId, accountId, ReservationType.CREDIT).isPresent())
      return;
    reservations.persist(new Reservation(lraId, accountId, amount, ReservationType.CREDIT));
  }

  /** Applies every hold taken for this saga. Safe to call repeatedly. */
  @Transactional
  public ParticipantStatus complete(String lraId)
  {
    for (Reservation reservation : reservations.findByLra(lraId))
    {
      if (reservation.getState() != ReservationState.PENDING)
        continue;
      Account account = requireAccount(reservation.getAccountId());
      account.setBalance(account.getBalance().add(reservation.signedAmount()));
      reservation.setState(ReservationState.CONFIRMED);
    }
    return ParticipantStatus.Completed;
  }

  /**
   * Releases every hold taken for this saga. Because the balances were never
   * touched there is no compensating write to make — and therefore no
   * compensating write that can fail.
   */
  @Transactional
  public ParticipantStatus compensate(String lraId)
  {
    for (Reservation reservation : reservations.findByLra(lraId))
      if (reservation.getState() == ReservationState.PENDING)
        reservation.setState(ReservationState.RELEASED);
    return ParticipantStatus.Compensated;
  }

  public Optional<ParticipantStatus> status(String lraId)
  {
    List<Reservation> found = reservations.findByLra(lraId);
    if (found.isEmpty())
      return Optional.empty();
    if (found.stream().anyMatch(r -> r.getState() == ReservationState.PENDING))
      return Optional.of(ParticipantStatus.Active);
    if (found.stream().allMatch(r -> r.getState() == ReservationState.CONFIRMED))
      return Optional.of(ParticipantStatus.Completed);
    return Optional.of(ParticipantStatus.Compensated);
  }

  @Transactional
  public void forget(String lraId)
  {
    reservations.deleteByLra(lraId);
  }

  /**
   * What the account can actually spend right now: the settled balance minus
   * everything held by sagas still in flight. This is the number a saga must
   * check against — the raw balance would allow two concurrent transfers to each
   * reserve the same money.
   */
  @Transactional
  public AvailabilityDto availability(String accountId)
  {
    Account account = requireAccount(accountId);
    BigDecimal held = reservations.pendingDebits(accountId);
    return new AvailabilityDto(accountId, account.getBalance(), held, account.getBalance().subtract(held));
  }

  private BigDecimal available(Account account)
  {
    return account.getBalance().subtract(reservations.pendingDebits(account.getId()));
  }

  private Account requireAccount(String accountId)
  {
    return accountRepository.getAccount(accountId)
      .orElseThrow(() -> new AccountNotFoundException(accountId));
  }

  private void requirePositive(String accountId, BigDecimal amount)
  {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidAmountException(accountId);
  }
}
