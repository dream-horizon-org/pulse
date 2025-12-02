package in.horizonos.pulseserver.rest.exception;


import com.dream11.rest.exception.RestException;

public class HealthCheckException extends RestException {

  public HealthCheckException(String responseMessage) {
    super("HEALTHCHECK_FAILED", responseMessage, 503);
  }

  @Override
  public String toString() {
    return this.getMessage();
  }

}

