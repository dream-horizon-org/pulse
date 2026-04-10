package org.dreamhorizon.pulseserver.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiProxyUpstreamResultTest {

  @Nested
  class IsSuccessfulBuffered {

    @Test
    void shouldReturnFalseWhenResultIsNull() {
      assertThat(AiProxyUpstreamResult.isSuccessfulBuffered(null)).isFalse();
    }

    @Test
    void shouldReturnTrueForBuffered2xxWithNonBlankBody() {
      AiProxyUpstreamResult result =
          AiProxyUpstreamResult.buffered(200, "application/json", "{\"ok\":true}");
      assertThat(AiProxyUpstreamResult.isSuccessfulBuffered(result)).isTrue();
    }

    @Test
    void shouldReturnFalseForNon2xx() {
      AiProxyUpstreamResult result =
          AiProxyUpstreamResult.buffered(500, "application/json", "{\"error\":\"x\"}");
      assertThat(AiProxyUpstreamResult.isSuccessfulBuffered(result)).isFalse();
    }

    @Test
    void shouldReturnFalseForBlankBody() {
      AiProxyUpstreamResult result = AiProxyUpstreamResult.buffered(200, "application/json", "  ");
      assertThat(AiProxyUpstreamResult.isSuccessfulBuffered(result)).isFalse();
    }

    @Test
    void shouldReturnFalseForStreamingResult() {
      AiProxyUpstreamResult result =
          AiProxyUpstreamResult.streaming(
              200,
              "text/event-stream",
              new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
      assertThat(AiProxyUpstreamResult.isSuccessfulBuffered(result)).isFalse();
    }
  }
}
