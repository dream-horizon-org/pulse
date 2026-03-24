package org.dreamhorizon.pulseserver.filter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class StreamingSafeLoggerFilterTest {

  private static final String REQUEST_START_TIME = "REQUEST_START_TIME";

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  ContainerResponseContext responseContext;

  @Mock
  UriInfo uriInfo;

  StreamingSafeLoggerFilter filter = new StreamingSafeLoggerFilter();

  private void setupStreamingEntity() {
    StreamingOutput streamingEntity = output -> {
    };
    when(responseContext.hasEntity()).thenReturn(true);
    when(responseContext.getEntity()).thenReturn(streamingEntity);
  }

  private void setupResponseTimeLogging() {
    when(requestContext.getMethod()).thenReturn("GET");
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("v1/ai/chat");
    when(responseContext.getStatus()).thenReturn(200);
  }

  @Nested
  class StreamingOutputDetection {

    @Test
    void shouldReturnEarlyWhenEntityIsStreamingOutput() throws IOException {
      setupStreamingEntity();

      filter.filter(requestContext, responseContext);

      verify(responseContext, atLeastOnce()).hasEntity();
      verify(responseContext, atLeastOnce()).getEntity();
    }

    @Test
    void shouldLogResponseTimeAndRemovePropertyWhenStreamingOutputAndStartTimeExists()
        throws IOException {
      setupStreamingEntity();
      setupResponseTimeLogging();
      when(requestContext.getProperty(REQUEST_START_TIME)).thenReturn(System.currentTimeMillis());

      filter.filter(requestContext, responseContext);

      verify(requestContext).getProperty(REQUEST_START_TIME);
      verify(requestContext).removeProperty(eq(REQUEST_START_TIME));
    }

    @Test
    void shouldNotCallRemovePropertyWhenStreamingOutputAndNoStartTime() throws IOException {
      setupStreamingEntity();
      when(requestContext.getProperty(REQUEST_START_TIME)).thenReturn(null);

      filter.filter(requestContext, responseContext);

      verify(requestContext).getProperty(REQUEST_START_TIME);
      verify(requestContext, never()).removeProperty(eq(REQUEST_START_TIME));
    }
  }

  @Nested
  @MockitoSettings(strictness = Strictness.LENIENT)
  class NonStreamingResponses {

    @Test
    void shouldDelegateToParentFilterWhenEntityIsNotStreamingOutput() throws IOException {
      setupResponseTimeLogging();
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn("{\"message\":\"ok\"}");

      filter.filter(requestContext, responseContext);

      verify(responseContext, atLeastOnce()).hasEntity();
      verify(responseContext, atLeastOnce()).getEntity();
    }

    @Test
    void shouldDelegateToParentFilterWhenNoEntity() throws IOException {
      setupResponseTimeLogging();
      when(responseContext.hasEntity()).thenReturn(false);

      filter.filter(requestContext, responseContext);

      verify(responseContext, atLeastOnce()).hasEntity();
    }

    @Test
    void shouldDelegateToParentFilterWhenEntityIsString() throws IOException {
      setupResponseTimeLogging();
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn("plain text");

      filter.filter(requestContext, responseContext);

      verify(responseContext, atLeastOnce()).getEntity();
    }
  }
}
