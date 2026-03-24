package org.dreamhorizon.pulseserver.service.ai;

import java.io.InputStream;
import java.util.Objects;

/**
 * Result of calling the Pulse AI upstream via {@link AiProxyService}. Contains no JAX-RS types so
 * the service layer stays independent of REST APIs. The controller maps this to
 * {@link jakarta.ws.rs.core.Response} (including {@code StreamingOutput} for SSE).
 */
public final class AiProxyUpstreamResult {

  private final int statusCode;
  private final String mediaType;
  private final String bufferedBody;
  private final InputStream streamBody;

  private AiProxyUpstreamResult(
      int statusCode,
      String mediaType,
      String bufferedBody,
      InputStream streamBody) {
    this.statusCode = statusCode;
    this.mediaType = Objects.requireNonNullElse(mediaType, "application/json");
    this.bufferedBody = bufferedBody;
    this.streamBody = streamBody;
  }

  /** Full response body read into memory (JSON, etc.). */
  public static AiProxyUpstreamResult buffered(int statusCode, String mediaType, String body) {
    return new AiProxyUpstreamResult(statusCode, mediaType, Objects.requireNonNull(body), null);
  }

  /**
   * Streamed body (e.g. SSE). The {@code InputStream} must be read when the HTTP response is
   * written; caller (controller) is responsible for closing it via the pipe helper.
   */
  public static AiProxyUpstreamResult streaming(
      int statusCode, String mediaType, InputStream body) {
    return new AiProxyUpstreamResult(
        statusCode, mediaType, null, Objects.requireNonNull(body, "body"));
  }

  public static AiProxyUpstreamResult badGateway() {
    return buffered(502, "application/json", "{\"error\":\"AI service unavailable\"}");
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getMediaType() {
    return mediaType;
  }

  public boolean isBuffered() {
    return streamBody == null;
  }

  public String getBufferedBody() {
    return bufferedBody;
  }

  public InputStream getStreamBody() {
    return streamBody;
  }
}
