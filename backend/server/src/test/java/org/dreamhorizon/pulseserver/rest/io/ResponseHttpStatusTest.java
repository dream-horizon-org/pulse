package org.dreamhorizon.pulseserver.rest.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.rest.Error;
import org.junit.jupiter.api.Test;

/** Covers {@link Response} status helpers used with {@code PulseResponseHttpStatusFilter}. */
class ResponseHttpStatusTest {

  @Test
  void successfulResponseDefaultsTo200() {
    Response<String> r = Response.successfulResponse("data");
    assertThat(r.getData()).isEqualTo("data");
    assertThat(r.getHttpStatusCode()).isEqualTo(200);
  }

  @Test
  void successfulResponseWithExplicitStatus() {
    Response<String> r = Response.successfulResponse("data", 202);
    assertThat(r.getHttpStatusCode()).isEqualTo(202);
  }

  @Test
  void errorResponseCarriesStatus() {
    Error err = Error.of("E1", "msg");
    Response<?> r = Response.errorResponse(err, 503);
    assertThat(r.getError()).isEqualTo(err);
    assertThat(r.getHttpStatusCode()).isEqualTo(503);
  }
}
