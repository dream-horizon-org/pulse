package org.dreamhorizon.pulseserver.filter;

import com.dream11.rest.filter.LoggerFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Extends the library's {@link LoggerFilter} to safely handle {@link StreamingOutput}
 * entities (e.g., SSE streams). The default filter tries to serialize every response
 * entity with Jackson, which fails for lambdas. This filter skips entity replacement
 * for StreamingOutput, allowing RESTEasy to write the stream directly.
 */
@Slf4j
public class StreamingSafeLoggerFilter extends LoggerFilter {

  private static final String REQUEST_START_TIME = "REQUEST_START_TIME";

  @Override
  public void filter(ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) throws IOException {
    boolean isStreamingEntity = responseContext.hasEntity()
        && responseContext.getEntity() instanceof StreamingOutput;

    if (isStreamingEntity) {
      logResponseTime(requestContext, responseContext);
      return;
    }

    super.filter(requestContext, responseContext);
  }

  private void logResponseTime(ContainerRequestContext requestContext,
      ContainerResponseContext responseContext) {
    Object startTime = requestContext.getProperty(REQUEST_START_TIME);
    boolean hasStartTime = startTime != null;
    if (hasStartTime) {
      long elapsed = System.currentTimeMillis() - (Long) startTime;
      log.debug("[RESPONSE TIME] Time taken for route: {} {} : {}ms, Status code : {}",
          requestContext.getMethod(),
          requestContext.getUriInfo().getPath(),
          elapsed,
          responseContext.getStatus());
      requestContext.removeProperty(REQUEST_START_TIME);
    }
  }
}
