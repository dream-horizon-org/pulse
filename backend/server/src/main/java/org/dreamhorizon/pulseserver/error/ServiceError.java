package org.dreamhorizon.pulseserver.error;

import com.dream11.rest.exception.RestError;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@ToString
@RequiredArgsConstructor
public enum ServiceError implements RestError {
  SERVICE_UNKNOWN_EXCEPTION("pulse-server-UNKNOWN-EXCEPTION", "Something went wrong", 500),
  INVALID_REQUEST_BODY("BE1001", "Invalid JSON provided", 400),
  INVALID_JSON("BE1001", "Invalid JSON provided", 400),
  INCORRECT_OR_MISSING_BODY_PARAMETERS("BE1002", "Incorrect or Missing body parameters.", 400),
  INVALID_REQUEST_PARAM("BE1003", "Invalid Request Parameters", 400),
  INCORRECT_OR_MISSING_QUERY_PARAMETERS("BE1004", "Incorrect or Missing query parameters.", 400),
  INCORRECT_OR_MISSING_HEADER_PARAMETERS("BE1005", "Incorrect or Missing header parameters.", 400),
  INCORRECT_OR_MISSING_PATH_PARAMETERS("BE1006", "Incorrect or Missing path parameters.", 400),
  INTERNAL_SERVER_ERROR("BE1007", "Something went wrong", 500),
  INVALID_CHARACTER("BE1009", "Unsupported characters found in request", 400),
  INVALID_NAME_SUPPLIED("400", "Invalid Name supplied", 400),
  UNAUTHORISED("401", "Unauthorised", 401),
  FORBIDDEN("403", "Forbidden", 403),
  AUTHENTICATION_BAD_REQUEST("400", "Bad Request", 400),
  DATABASE_ERROR("500", "Database Error", 500),
  DATAFLOW_SERVICE_ERROR("500", "Dataflow Service Error", 500),
  NOT_FOUND("404", "Not Found", 404),
  CRON_SERVICE_ERROR("500", "Cron Service Error", 500),
  DUPLICATE_INTERACTION_NAME_ERROR("500", "Interaction name already present", 500),
  USER_NOT_FOUND("400", "User not found", 400);

  private static final Logger log = LoggerFactory.getLogger(ServiceError.class);
  final String errorCode;
  final String errorMessage;
  final int httpStatusCode;

  public WebApplicationException getException() {
    return getCustomException(null, null, 0);
  }

  public WebApplicationException getCustomException(String errorCause) {
    return getCustomException(null, errorCause, 0);
  }

  public WebApplicationException getCustomException(String errorMessage, String errorCause) {
    return getCustomException(errorMessage, errorCause, 0);
  }

  public WebApplicationException getCustomException(String errorMessage, int errorCode) {
    return getCustomException(errorMessage, null, errorCode);
  }

  public WebApplicationException getCustomException(int httpStatusCode) {
    return getCustomException(null, null, httpStatusCode);
  }

  public WebApplicationException getCustomException(
      String errorMessage, String errorCause, int httpStatusCode) {
    errorMessage = errorMessage == null ? this.errorMessage : errorMessage;
    errorCause = errorCause == null ? this.errorMessage : errorCause;
    httpStatusCode = httpStatusCode == 0 ? this.httpStatusCode : httpStatusCode;

    log.info("message {} cause {} code {}", errorMessage, errorCause, httpStatusCode);
    String errCode = Integer.toString(httpStatusCode);
    Response response =
        Response.status(httpStatusCode)
            .header("Content-Type", "application/json")
            .entity(new ExceptionResponseEntity(errCode, errorMessage, errorCause))
            .build();
    log.info("response {}", response);
    return new WebApplicationException(errorCause, response);
  }

  public WebApplicationException getCustomNotFoundException(
      String errorMessage, String errorCause, int httpStatusCode) {
    errorMessage = errorMessage == null ? this.errorMessage : errorMessage;
    errorCause = errorCause == null ? this.errorMessage : errorCause;
    String customCause =
        "Value of "
            + errorCause.substring(errorCause.indexOf("\"") + 1, errorCause.lastIndexOf("\""))
            + " is invalid";
    Response response =
        Response.status(httpStatusCode)
            .header("Content-Type", "application/json")
            .entity(new ExceptionResponseEntity(this.errorCode, errorMessage, customCause))
            .build();
    return new WebApplicationException(errorCause, response);
  }

  public static class ExceptionResponseEntity {
    public Error error;

    public ExceptionResponseEntity(String errorCode, String errorMessage, String errorCause) {
      this.error = new Error(errorCode, errorMessage, errorCause);
    }

    public static class Error {
      public String code;

      public String message;

      public String cause;

      Error(String errorCode, String errorMessage, String errorCause) {
        this.code = errorCode;
        this.message = errorMessage;
        this.cause = errorCause;
      }
    }
  }
}
