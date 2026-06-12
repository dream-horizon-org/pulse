package org.dreamhorizon.pulseserver.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.dreamhorizon.pulseserver.rest.io.Response;

/**
 * Applies {@link Response#getHttpStatusCode()} to the JAX-RS response when not 200 (e.g. 202
 * Accepted), before {@link StreamingSafeLoggerFilter} serializes the entity to a string.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class PulseResponseHttpStatusFilter implements ContainerResponseFilter {

  @Override
  public void filter(
      ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) {
    Object entity = responseContext.getEntity();
    if (entity instanceof Response<?> r) {
      int code = r.getHttpStatusCode();
      if (code != 200) {
        responseContext.setStatus(code);
      }
    }
  }
}
