package org.dreamhorizon.pulseserver.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceErrorTest {

  @Nested
  class TestEnumValues {

    @Test
    void shouldHaveExpectedErrorCodes() {
      assertThat(ServiceError.NOT_FOUND.getErrorCode()).isEqualTo("404");
      assertThat(ServiceError.UNAUTHORISED.getErrorCode()).isEqualTo("401");
      assertThat(ServiceError.FORBIDDEN.getErrorCode()).isEqualTo("403");
      assertThat(ServiceError.INVALID_REQUEST_BODY.getErrorCode()).isEqualTo("BE1001");
      assertThat(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode()).isEqualTo("BE1007");
    }

    @Test
    void shouldHaveExpectedHttpStatusCodes() {
      assertThat(ServiceError.NOT_FOUND.getHttpStatusCode()).isEqualTo(404);
      assertThat(ServiceError.UNAUTHORISED.getHttpStatusCode()).isEqualTo(401);
      assertThat(ServiceError.FORBIDDEN.getHttpStatusCode()).isEqualTo(403);
      assertThat(ServiceError.INTERNAL_SERVER_ERROR.getHttpStatusCode()).isEqualTo(500);
    }

    @Test
    void shouldHaveExpectedMessages() {
      assertThat(ServiceError.NOT_FOUND.getErrorMessage()).isEqualTo("Not Found");
      assertThat(ServiceError.INVALID_REQUEST_BODY.getErrorMessage()).isEqualTo("Invalid JSON provided");
    }
  }

  @Nested
  class TestGetException {

    @Test
    void shouldReturnWebApplicationExceptionWithDefaultValues() {
      WebApplicationException ex = ServiceError.NOT_FOUND.getException();

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse()).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class TestGetCustomExceptionWithErrorCause {

    @Test
    void shouldUseCustomErrorCause() {
      WebApplicationException ex = ServiceError.NOT_FOUND.getCustomException("custom cause");

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class TestGetCustomExceptionWithMessageAndCause {

    @Test
    void shouldUseCustomMessageAndCause() {
      WebApplicationException ex = ServiceError.INVALID_REQUEST_BODY
          .getCustomException("Custom message", "Custom cause");

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(400);
    }
  }

  @Nested
  class TestGetCustomExceptionWithMessageAndCode {

    @Test
    void shouldUseCustomMessageAndHttpCode() {
      WebApplicationException ex = ServiceError.NOT_FOUND.getCustomException("Resource missing", 404);

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class TestGetCustomExceptionWithHttpStatusCode {

    @Test
    void shouldUseCustomHttpStatusCode() {
      WebApplicationException ex = ServiceError.UNAUTHORISED.getCustomException(401);

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(401);
    }
  }

  @Nested
  class TestGetCustomExceptionFull {

    @Test
    void shouldUseAllCustomValues() {
      WebApplicationException ex = ServiceError.FORBIDDEN
          .getCustomException("Access denied", "User lacks permission", 403);

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void shouldUseDefaultsWhenNullsPassed() {
      WebApplicationException ex = ServiceError.NOT_FOUND.getCustomException(null, null, 0);

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class TestGetCustomNotFoundException {

    @Test
    void shouldExtractValueFromErrorCause() {
      WebApplicationException ex = ServiceError.INVALID_NAME_SUPPLIED
          .getCustomNotFoundException("Invalid name", "\"projectId\" is invalid", 400);

      assertThat(ex).isNotNull();
      assertThat(ex.getResponse().getStatus()).isEqualTo(400);
    }
  }

  @Nested
  class TestExceptionResponseEntity {

    @Test
    void shouldCreateExceptionResponseEntityWithErrorDetails() {
      ServiceError.ExceptionResponseEntity entity =
          new ServiceError.ExceptionResponseEntity("404", "Not Found", "Resource not found");

      assertThat(entity).isNotNull();
      assertThat(entity.error).isNotNull();
      assertThat(entity.error.code).isEqualTo("404");
      assertThat(entity.error.message).isEqualTo("Not Found");
      assertThat(entity.error.cause).isEqualTo("Resource not found");
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldReturnStringRepresentation() {
      String str = ServiceError.NOT_FOUND.toString();

      assertThat(str).contains("NOT_FOUND");
      assertThat(str).contains("404");
    }
  }
}
