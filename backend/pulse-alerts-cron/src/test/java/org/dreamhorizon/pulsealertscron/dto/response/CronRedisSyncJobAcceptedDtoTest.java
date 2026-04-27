package org.dreamhorizon.pulsealertscron.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CronRedisSyncJobAcceptedDtoTest {

  @Test
  void shouldParseEnvelope() {
    String json = """
        {"data":{"jobId":42,"deduplicated":true,"jobType":"USAGE_CREDITS_TO_REDIS"},"error":null}
        """;

    Optional<CronRedisSyncJobAcceptedDto> parsed = CronRedisSyncJobAcceptedDto.tryParse(json);

    assertThat(parsed).isPresent();
    assertThat(parsed.get().getJobId()).isEqualTo(42L);
    assertThat(parsed.get().isDeduplicated()).isTrue();
    assertThat(parsed.get().getJobType()).isEqualTo("USAGE_CREDITS_TO_REDIS");
  }

  @Test
  void shouldReturnEmptyForInvalidBody() {
    assertThat(CronRedisSyncJobAcceptedDto.tryParse("not json")).isEmpty();
    assertThat(CronRedisSyncJobAcceptedDto.tryParse("{\"data\":{}}")).isEmpty();
    assertThat(CronRedisSyncJobAcceptedDto.tryParse(null)).isEmpty();
  }
}
