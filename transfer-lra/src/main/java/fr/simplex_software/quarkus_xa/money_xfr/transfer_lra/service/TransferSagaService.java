package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.service;

import fr.simplex_software.quarkus_xa.money_xfr.common.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.client.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.repository.*;
import io.quarkus.narayana.jta.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.rest.client.inject.*;

import java.math.*;
import java.time.*;
import java.util.*;

@ApplicationScoped
public class TransferSagaService
{
  @Inject
  TransferRepository transferRepository;
  @Inject
  @RestClient
  AccountLraClient accountClient;

  public TransferDto processTransfer(String lraId, TransferRequest request)
  {
    if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidAmountException(request.getSourceAccountId());

    Transfer transfer = QuarkusTransaction.requiringNew().call(() ->
    {
      Transfer pending = new Transfer(Common.getRandomId(), lraId, request.getSourceAccountId(),
        request.getDestinationAccountId(), request.getAmount(), request.getCurrency(),
        TransferStatus.PENDING, LocalDateTime.now());
      transferRepository.persist(pending);
      return pending;
    });

    try
    {
      accountClient.reserveDebit(request.getSourceAccountId(), request.getAmount());
      accountClient.reserveCredit(request.getDestinationAccountId(), request.getAmount());
    }
    catch (WebApplicationException wae)
    {
      throw RemoteAccountException.from(wae);
    }
    return transfer.toDto();
  }

  @Transactional
  public ParticipantStatus complete(String lraId)
  {
    transferRepository.findByLra(lraId)
      .filter(transfer -> transfer.getStatus() == TransferStatus.PENDING)
      .ifPresent(transfer -> transfer.setStatus(TransferStatus.COMPLETED));
    return ParticipantStatus.Completed;
  }

  @Transactional
  public ParticipantStatus compensate(String lraId)
  {
    transferRepository.findByLra(lraId)
      .filter(transfer -> transfer.getStatus() == TransferStatus.PENDING)
      .ifPresent(transfer -> transfer.setStatus(TransferStatus.COMPENSATED));
    return ParticipantStatus.Compensated;
  }

  public Optional<ParticipantStatus> status(String lraId)
  {
    return transferRepository.findByLra(lraId).map(transfer -> switch (transfer.getStatus())
    {
      case COMPLETED -> ParticipantStatus.Completed;
      case COMPENSATED -> ParticipantStatus.Compensated;
      default -> ParticipantStatus.Active;
    });
  }

  @Transactional
  public void forget(String lraId)
  {
    transferRepository.findByLra(lraId).ifPresent(transfer -> transfer.setLraId(null));
  }

  public TransferDto getTransfer(String transferId)
  {
    return transferRepository.findByTransferId(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId))
      .toDto();
  }

  public List<TransferDto> getTransfers()
  {
    return transferRepository.findAllTransfers().stream().map(Transfer::toDto).toList();
  }

  @Transactional
  public void updateTransfer(String transferId, TransferDto transferDto)
  {
    Transfer transfer = transferRepository.findByTransferId(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId));
    transfer.setSourceAccountId(transferDto.getSourceAccountId());
    transfer.setDestinationAccountId(transferDto.getDestinationAccountId());
    transfer.setAmount(transferDto.getAmount());
    transfer.setCurrency(transferDto.getCurrency());
    transfer.setStatus(transferDto.getStatus());
    transfer.setTimestamp(transferDto.getTimestamp());
  }

  @Transactional
  public void deleteTransfer(String transferId)
  {
    Transfer transfer = transferRepository.findByTransferId(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId));
    transferRepository.delete(transfer);
  }
}
