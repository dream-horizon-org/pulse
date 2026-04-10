package org.dreamhorizon.pulseserver.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionReplayS3Config {
  private String bucket;
  private String endpoint;
  private String region;
  private String accessKeyId;
  private String secretAccessKey;
}
