package org.dreamhorizon.pulseserver.rest.exception;

import com.dream11.rest.exception.RestException;

/**
 * Exception thrown when an operation is forbidden due to business rules.
 * Returns HTTP 403 Forbidden.
 */
public class ForbiddenOperationException extends RestException {

  public ForbiddenOperationException(String message) {
    super("FORBIDDEN_OPERATION", message, 403);
  }

  public ForbiddenOperationException(String code, String message) {
    super(code, message, 403);
  }

  @Override
  public String toString() {
    return this.getMessage();
  }
}
