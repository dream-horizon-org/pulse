package org.dreamhorizon.pulseserver.filter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamingSafeLoggerFilterTest {

  private static final String REQUEST_START_TIME = "REQUEST_START_TIME";

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  ContainerResponseContext responseContext;

  @Mock
  UriInfo uriInfo;

  @Spy
  StreamingSafeLoggerFilter filter;

  @BeforeEach
  void setUp() {
    when(requestContext.getMethod()).thenReturn("GET");
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("v1/ai/chat");
    when(responseContext.getStatus()).thenReturn(200);
  }

  @Nested
  class StreamingOutputDetection {

    @Test
    void shouldReturnEarlyWhenEntityIsStreamingOutput() throws IOException {
      StreamingOutput streamingEntity = output -> {
      };
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn(streamingEntity);

      filter.filter(requestContext, responseContext);

      verify(responseContext).hasEntity();
      verify(responseContext).getEntity();
    }

    @Test
    void shouldLogResponseTimeAndRemovePropertyWhenStreamingOutputAndStartTimeExists()
        throws IOException {
      StreamingOutput streamingEntity = output -> {
      };
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn(streamingEntity);
      when(requestContext.getProperty(REQUEST_START_TIME)).thenReturn(System.currentTimeMillis());

      filter.filter(requestContext, responseContext);

      verify(requestContext).getProperty(REQUEST_START_TIME);
      verify(requestContext).removeProperty(eq(REQUEST_START_TIME));
    }

    @Test
    void shouldNotCallRemovePropertyWhenStreamingOutputAndNoStartTime() throws IOException {
      StreamingOutput streamingEntity = output -> {
      };
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn(streamingEntity);
      when(requestContext.getProperty(REQUEST_START_TIME)).thenReturn(null);

      filter.filter(requestContext, responseContext);

      verify(requestContext).getProperty(REQUEST_START_TIME);
      verify(requestContext, never()).removeProperty(eq(REQUEST_START_TIME));
    }
  }

  @Nested
  class NonStreamingResponses {

    @Test
    void shouldDelegateToParentFilterWhenEntityIsNotStreamingOutput() throws IOException {
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn("{\"message\":\"ok\"}");

      filter.filter(requestContext, responseContext);

      verify(responseContext).hasEntity();
      verify(responseContext).getEntity();
    }

    @Test
    void shouldDelegateToParentFilterWhenNoEntity() throws IOException {
      when(responseContext.hasEntity()).thenReturn(false);

      filter.filter(requestContext, responseContext);

      verify(responseContext).hasEntity();
      verify(responseContext, never()).getEntity();
    }

    @Test
    void shouldDelegateToParentFilterWhenEntityIsString() throws IOException {
      when(responseContext.hasEntity()).thenReturn(true);
      when(responseContext.getEntity()).thenReturn("plain text");

      filter.filter(requestContext, responseContext);

      verify(responseContext).getEntity();
    }
  }
}
