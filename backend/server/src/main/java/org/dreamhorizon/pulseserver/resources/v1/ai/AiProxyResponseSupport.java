package org.dreamhorizon.pulseserver.resources.v1.ai;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * JAX-RS helpers for mapping {@link AiProxyUpstreamResult} to {@link Response}
 * (buffered JSON vs {@link StreamingOutput} SSE). Keeps {@link AiProxyController} thin.
 */
@Slf4j
public final class AiProxyResponseSupport {

  public static final int DEFAULT_STREAM_BUFFER_SIZE = 1024;

  private AiProxyResponseSupport() {}

  public static Response toJaxRsResponse(AiProxyUpstreamResult result, int streamBufferSize) {
    if (result.isBuffered()) {
      return Response.status(result.getStatusCode())
          .entity(result.getBufferedBody())
          .type(result.getMediaType())
          .build();
    }
    InputStream body = result.getStreamBody();
    StreamingOutput stream = output -> pipeStream(body, output, streamBufferSize);
    return Response.status(result.getStatusCode())
        .entity(stream)
        .type(result.getMediaType())
        .build();
  }

  /**
   * Copies bytes from an input stream to a streaming JAX-RS output (SSE / chunked).
   */
  public static void pipeStream(InputStream body, java.io.OutputStream output, int bufferSize)
      throws IOException {
    try (InputStream is = body) {
      byte[] buf = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = is.read(buf)) != -1) {
        output.write(buf, 0, bytesRead);
        output.flush();
      }
    }
  }

  public static String rawQuery(UriInfo uriInfo) {
    return uriInfo.getRequestUri().getRawQuery();
  }

  public static String readBodyUtf8(InputStream bodyStream) {
    if (bodyStream == null) {
      return null;
    }
    try {
      byte[] bytes = bodyStream.readAllBytes();
      boolean isEmpty = bytes.length == 0;
      return isEmpty ? null : new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.debug("Failed to read request body: {}", e.getMessage());
      return null;
    }
  }
}
