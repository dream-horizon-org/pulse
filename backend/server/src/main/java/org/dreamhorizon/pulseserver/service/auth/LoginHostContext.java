package org.dreamhorizon.pulseserver.service.auth;

import lombok.Builder;
import lombok.Value;

/**
 * Captures browser-side host and optional forwarded host for
 * workspace tenant resolution during system-role login.
 */
@Value
@Builder
public class LoginHostContext {
  /** From LoginRequest.pulseClientHost — window.location.host on the SPA. */
  String pulseClientHost;

  /** From X-Forwarded-Host header (trusted proxy only, optional). */
  String forwardedHost;

  public static LoginHostContext empty() {
    return LoginHostContext.builder().build();
  }
}
