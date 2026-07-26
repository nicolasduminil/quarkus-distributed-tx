package fr.simplex_software.quarkus_xa.money_xfr.transfer_lra.exception;

import fr.simplex_software.quarkus_xa.money_xfr.common.exception.*;
import jakarta.ws.rs.core.*;

public class TransferNotFoundException extends ApplicationException
{
  public TransferNotFoundException(String transferId)
  {
    super(Response.Status.NOT_FOUND, "### Transfer with ID %s hasn't been found.".formatted(transferId));
  }
}
