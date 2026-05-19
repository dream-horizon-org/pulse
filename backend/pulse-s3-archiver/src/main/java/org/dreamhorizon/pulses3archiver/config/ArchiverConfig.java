package org.dreamhorizon.pulses3archiver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchiverConfig {
  private long shutdownGracePeriod = 5L;
  private long flushIntervalMs = 30000L;
  private String stagingDir = "/tmp/pulse-archiver";
  private long stagingMaxBytes = 2147483648L;
}
