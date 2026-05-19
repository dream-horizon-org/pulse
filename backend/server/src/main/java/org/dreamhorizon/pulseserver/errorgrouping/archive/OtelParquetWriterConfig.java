package org.dreamhorizon.pulseserver.errorgrouping.archive;

import lombok.Value;

@Value
public class OtelParquetWriterConfig {
  long rowGroupBytes;
  long pageBytes;
  long flushSizeBytes;
  long flushAgeMs;

  static OtelParquetWriterConfig from(StackTraceArchiveConfig config) {
    return new OtelParquetWriterConfig(
        config.getRowGroupBytes(),
        config.getPageBytes(),
        config.getFlushSizeBytes(),
        config.getFlushAgeMs());
  }
}
