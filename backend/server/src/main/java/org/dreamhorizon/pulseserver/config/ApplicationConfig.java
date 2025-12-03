package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class ApplicationConfig {
  public String cronManagerBaseUrl;
  public String serviceUrl;
  public Integer shutdownGracePeriod;
  public String googleOAuthClientId;
  public Boolean googleOAuthEnabled;
  public String jwtSecret;
}
