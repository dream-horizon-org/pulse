package org.dreamhorizon.pulseserver.resources.notification.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SlackOAuthCallbackRequestTest {

  @Nested
  class HasError {

    @Test
    void shouldReturnTrueWhenErrorSet() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setError("access_denied");

      assertThat(request.hasError()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenErrorNull() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();

      assertThat(request.hasError()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenErrorBlank() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setError("   ");

      assertThat(request.hasError()).isFalse();
    }
  }

  @Nested
  class IsValid {

    @Test
    void shouldReturnTrueWhenCodeAndProjectIdPresent() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code-123");
      request.setProjectId("proj-1");

      assertThat(request.isValid()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenCodeMissing() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setProjectId("proj-1");

      assertThat(request.isValid()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenProjectIdMissing() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code-123");

      assertThat(request.isValid()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenCodeBlank() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("  ");
      request.setProjectId("proj-1");

      assertThat(request.isValid()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenProjectIdBlank() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code-123");
      request.setProjectId("  ");

      assertThat(request.isValid()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenHasError() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setError("access_denied");

      assertThat(request.isValid()).isTrue();
    }
  }

  @Nested
  class GetValidationError {

    @Test
    void shouldReturnNullWhenHasError() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setError("access_denied");

      assertThat(request.getValidationError()).isNull();
    }

    @Test
    void shouldReturnErrorWhenCodeMissing() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setProjectId("proj-1");

      assertThat(request.getValidationError())
          .isEqualTo("Authorization code is required");
    }

    @Test
    void shouldReturnErrorWhenProjectIdMissing() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code-123");

      assertThat(request.getValidationError())
          .isEqualTo("Project ID (state) is required");
    }

    @Test
    void shouldReturnNullWhenValid() {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code-123");
      request.setProjectId("proj-1");

      assertThat(request.getValidationError()).isNull();
    }
  }
}
