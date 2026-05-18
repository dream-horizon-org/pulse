package org.dreamhorizon.pulses3archiver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WriterConfig {
  private long rowGroupBytes = 134217728L;
  private long pageBytes = 1048576L;
  private String compression = "ZSTD";
  private int zstdLevel = 3;
  private long flushSizeBytes = 268435456L;
  private long flushAgeMs = 300000L;
}
