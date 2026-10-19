package fr.simplex_software.quarkus_xa.money_xfr.transfer.service;

import fr.simplex_software.quarkus_xa.money_xfr.account_repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.domain.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.exception.*;
import fr.simplex_software.quarkus_xa.money_xfr.transfer.repository.*;
import fr.simplex_software.quarkus_xa.money_xfr.common.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.transaction.*;

import java.math.*;
import java.time.*;
import java.util.*;

@ApplicationScoped
public class TransferService
{
  @Inject
  AccountRepository accountRepository;
  @Inject
  TransferRepository transferRepository;

  @Transactional
  public TransferDto processTransfer(TransferRequest request)
  {
    if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)
      throw new InvalidAmountException(request.getSourceAccountId());
    Account source = accountRepository.getAccount(request.getSourceAccountId())
      .orElseThrow(() -> new AccountNotFoundException(request.getSourceAccountId()));
    Account destination = accountRepository.getAccount(request.getDestinationAccountId())
      .orElseThrow(() -> new AccountNotFoundException(request.getDestinationAccountId()));
    if (source.getBalance().compareTo(request.getAmount()) < 0)
      throw new InsufficientFundsException(source.getId());
    source.debit(request.getAmount());
    destination.credit(request.getAmount());
    Transfer transfer = new Transfer(Common.getRandomId(), source.getId(), destination.getId(),
      request.getAmount(), request.getCurrency(), TransferStatus.COMPLETED, LocalDateTime.now());
    transferRepository.createTransfer(transfer);
    return transfer.toDto();
  }

  public TransferDto getTransfer(String transferId)
  {
    return transferRepository.getTransfer(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId))
      .toDto();
  }

  public List<TransferDto> getTransfers()
  {
    return transferRepository.getTransfers().stream().map(Transfer::toDto).toList();
  }

  @Transactional
  public void updateTransfer(String transferId, TransferDto transferDto)
  {
    transferRepository.getTransfer(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId));
    transferDto.setTransferId(transferId);
    transferRepository.updateTransfer(new Transfer(transferDto));
  }

  @Transactional
  public void deleteTransfer(String transferId)
  {
    transferRepository.getTransfer(transferId)
      .orElseThrow(() -> new TransferNotFoundException(transferId));
    transferRepository.deleteTransfer(transferId);
  }
}
