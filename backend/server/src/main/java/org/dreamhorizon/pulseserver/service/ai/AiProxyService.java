package org.dreamhorizon.pulseserver.service.ai;

import java.util.concurrent.CompletionStage;

/**
 * Proxies authenticated AI agent HTTP calls to the configured Pulse AI upstream.
 */
public interface AiProxyService {

  /**
   * Forwards a request to the AI service.
   *
   * @param method        HTTP method (GET, POST, PUT, DELETE)
   * @param path          Path segment after {@code /v1/ai/} (no leading slash)
   * @param rawQuery      Raw query string without {@code ?}, or null/empty if none
   * @param body          JSON body for POST/PUT, or null
   * @param authorization Full Authorization header value (e.g. Bearer token)
   * @param projectId     X-Project-ID header value, or null if absent
   */
  CompletionStage<AiProxyUpstreamResult> proxy(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId);
}
