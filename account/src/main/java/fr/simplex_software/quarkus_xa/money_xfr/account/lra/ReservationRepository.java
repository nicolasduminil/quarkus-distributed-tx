package fr.simplex_software.quarkus_xa.money_xfr.account.lra;

import io.quarkus.hibernate.orm.panache.*;
import jakarta.enterprise.context.*;

import java.math.*;
import java.util.*;

@ApplicationScoped
public class ReservationRepository implements PanacheRepository<Reservation>
{
  public List<Reservation> findByLra(String lraId)
  {
    return list("lraId", lraId);
  }

  public Optional<Reservation> find(String lraId, String accountId, ReservationType type)
  {
    return find("lraId = ?1 and accountId = ?2 and type = ?3", lraId, accountId, type)
      .firstResultOptional();
  }

  public BigDecimal pendingDebits(String accountId)
  {
    return list("accountId = ?1 and type = ?2 and state = ?3",
      accountId, ReservationType.DEBIT, ReservationState.PENDING)
      .stream()
      .map(Reservation::getAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public long deleteByLra(String lraId)
  {
    return delete("lraId", lraId);
  }
}
