package org.dreamhorizon.pulsealertscron.config;

import com.google.inject.Singleton;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Data
@NoArgsConstructor
public class ApplicationConfig {
  private String pulseServerUrl;
  private long shutdownGracePeriod;
  private String serviceJwtSecret;
  
  private String clickhouseHost;
  private Integer clickhousePort;
  private String clickhouseDatabase;
  private String clickhouseUsername;
  private String clickhousePassword;
  
  private String redisHost;
  private Integer redisPort;
}
