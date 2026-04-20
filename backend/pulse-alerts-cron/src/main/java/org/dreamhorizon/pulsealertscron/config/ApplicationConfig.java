package org.dreamhorizon.pulsealertscron.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Singleton;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationConfig {
  private String pulseServerUrl;
  private long shutdownGracePeriod;
  private String serviceJwtSecret;
}
