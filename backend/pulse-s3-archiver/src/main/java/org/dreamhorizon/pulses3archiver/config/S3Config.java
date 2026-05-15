package org.dreamhorizon.pulses3archiver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Config {
  private String bucket;
  private String region = "ap-south-1";
  private long multipartThresholdBytes = 16777216L;
  private long multipartPartBytes = 16777216L;
  private int maxConcurrency = 32;
}
