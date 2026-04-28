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
  private String clusterName;
  /**
   * Opt-in for appending {@code SETTINGS use_query_condition_cache = 1}; also loaded from
   * {@code CLICKHOUSE_QUERY_CONDITION_CACHE_ENABLED} in clickhouse-default.conf.
   */
  private boolean queryConditionCacheEnabled;
}