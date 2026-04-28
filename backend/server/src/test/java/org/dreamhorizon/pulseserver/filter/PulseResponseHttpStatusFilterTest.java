package org.dreamhorizon.pulseserver.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PulseResponseHttpStatusFilterTest {

  private PulseResponseHttpStatusFilter filter;

  @Mock
  private ContainerRequestContext requestContext;

  @Mock
  private ContainerResponseContext responseContext;

  @BeforeEach
  void setUp() {
    filter = new PulseResponseHttpStatusFilter();
  }

  @Test
  void shouldSetStatusWhenResponseEntityHasNon200Code() {
    Response<String> entity = Response.successfulResponse("accepted", 202);
    when(responseContext.getEntity()).thenReturn(entity);

    filter.filter(requestContext, responseContext);

    verify(responseContext).setStatus(202);
  }

  @Test
  void shouldNotChangeStatusWhenEntityIsResponseWith200() {
    Response<String> entity = Response.successfulResponse("ok");
    assertThat(entity.getHttpStatusCode()).isEqualTo(200);
    when(responseContext.getEntity()).thenReturn(entity);

    filter.filter(requestContext, responseContext);

    verify(responseContext, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void shouldIgnoreNonResponseEntity() {
    when(responseContext.getEntity()).thenReturn("plain");

    filter.filter(requestContext, responseContext);

    verify(responseContext, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void shouldIgnoreNullEntity() {
    when(responseContext.getEntity()).thenReturn(null);

    filter.filter(requestContext, responseContext);

    verify(responseContext, never()).setStatus(org.mockito.ArgumentMatchers.anyInt());
  }
}
