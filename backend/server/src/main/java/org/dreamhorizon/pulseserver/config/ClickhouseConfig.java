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
public class ClickhouseConfig {
  private String r2dbcUrl;
  private String username;
  private String password;
  private Integer initsize;
  private Integer maxsize;
  private String host;
  private Integer port;
}