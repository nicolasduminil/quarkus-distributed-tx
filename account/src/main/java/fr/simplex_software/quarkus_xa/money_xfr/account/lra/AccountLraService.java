package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import org.eclipse.microprofile.lra.annotation.*;

import java.math.*;
import java.util.*;
import java.util.stream.*;

@ApplicationScoped
public class AccountLraService
{
  private static final List<ReservationState> SETTLEMENT_ORDER =
    List.of(ReservationState.PENDING, ReservationState.RELEASED, ReservationState.CONFIRMED);

  @Inject
  AccountRepository accountRepository;
  @Inject
  ReservationRepository reservations;

  @Transactional
  public void reserveDebit(String lraId, String accountId, BigDecimal amount)
  {
    Account account = requireAccount(accountId);
    requirePositive(accountId, amount);
    if (reservations.find(lraId, accountId, ReservationType.DEBIT).isEmpty())
    {
      if (available(account).compareTo(amount) < 0)
        throw new InsufficientFundsException(accountId);
      reservations.persist(new Reservation(lraId, accountId, amount, ReservationType.DEBIT));
    }
  }

  @Transactional
  public void reserveCredit(String lraId, String accountId, BigDecimal amount)
  {
    requireAccount(accountId);
    requirePositive(accountId, amount);
    if (reservations.find(lraId, accountId, ReservationType.CREDIT).isEmpty())
      reservations.persist(new Reservation(lraId, accountId, amount, ReservationType.CREDIT));
  }

  @Transactional
  public ParticipantStatus complete(String lraId)
  {
    pendingFor(lraId).forEach(this::confirm);
    return ParticipantStatus.Completed;
  }

  @Transactional
  public ParticipantStatus compensate(String lraId)
  {
    pendingFor(lraId).forEach(reservation -> reservation.setState(ReservationState.RELEASED));
    return ParticipantStatus.Compensated;
  }

  public Optional<ParticipantStatus> status(String lraId)
  {
    return reservations.findByLra(lraId).stream()
      .map(Reservation::getState)
      .min(Comparator.comparingInt(SETTLEMENT_ORDER::indexOf))
      .map(state -> switch (state)
      {
        case PENDING -> ParticipantStatus.Active;
        case RELEASED -> ParticipantStatus.Compensated;
        case CONFIRMED -> ParticipantStatus.Completed;
      });
  }

  @Transactional
  public void forget(String lraId)
  {
    reservations.deleteByLra(lraId);
  }

  @Transactional
  public AvailabilityDto availability(String accountId)
  {
    Account account = requireAccount(accountId);
    BigDecimal held = reservations.pendingDebits(accountId);
    return new AvailabilityDto(accountId, account.getBalance(), held, account.getBalance().subtract(held));
  }

  private Stream<Reservation> pendingFor(String lraId)
  {
    return reservations.findByLra(lraId).stream()
      .filter(reservation -> reservation.getState() == ReservationState.PENDING);
  }

  private void confirm(Reservation reservation)
  {
    Account account = requireAccount(reservation.getAccountId());
    account.setBalance(account.getBalance().add(reservation.signedAmount()));
    reservation.setState(ReservationState.CONFIRMED);
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
