package org.dreamhorizon.pulseserver.model;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class QueryConfiguration {
  private final String query;
  private final boolean useLegacySql;
  private final Integer timeoutMs;
  private final JobCreationMode jobCreationMode;
  private final String tenantId;
  private final String projectId;
  /**
   * When true, the server may append {@code SETTINGS use_query_condition_cache = 1} to the SQL so
   * ClickHouse can reuse granule filter results across similar dashboard-style reads (subject to
   * {@code ClickhouseConfig#queryConditionCacheEnabled}). The builder defaults to {@code false};
   * set {@code true} on high-reuse analytical reads only.
   */
  private final boolean useQueryConditionCache;

  private QueryConfiguration(
      String query,
      boolean useLegacySql,
      Integer timeoutMs,
      JobCreationMode jobCreationMode,
      String tenantId,
      String projectId,
      boolean useQueryConditionCache
  ) {
    this.query = query;
    this.useLegacySql = useLegacySql;
    this.timeoutMs = timeoutMs;
    this.jobCreationMode = jobCreationMode;
    this.tenantId = tenantId;
    this.projectId = projectId;
    this.useQueryConditionCache = useQueryConditionCache;
  }

  public static QueryConfigurationBuilder newQuery(@NotBlank @Valid String query) {
    return new QueryConfigurationBuilder(query);
  }

  @Getter
  @ToString
  public static class QueryConfigurationBuilder {
    private final String query;
    private Integer timeoutMs;
    private final Boolean useLegacySql = false;
    private JobCreationMode jobCreationMode;
    private String tenantId;
    private String projectId;
    /** Default {@code false}; set {@code true} for high-traffic / high-reuse dashboard-style reads. */
    private boolean useQueryConditionCache;

    private QueryConfigurationBuilder(String query) {
      this.query = query;
    }

    public QueryConfigurationBuilder timeoutMs(Integer timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    public QueryConfigurationBuilder jobCreationMode(JobCreationMode jobCreationMode) {
      this.jobCreationMode = jobCreationMode;
      return this;
    }

    public QueryConfigurationBuilder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public QueryConfigurationBuilder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public QueryConfigurationBuilder useQueryConditionCache(boolean useQueryConditionCache) {
      this.useQueryConditionCache = useQueryConditionCache;
      return this;
    }

    public QueryConfiguration build() {
      // TODO: Find better way to handle defaults
      if (timeoutMs == null) {
        timeoutMs = 60000;
      }

      return new QueryConfiguration(
          this.query,
          this.useLegacySql,
          this.timeoutMs,
          jobCreationMode,
          tenantId,
          projectId,
          this.useQueryConditionCache);
    }
  }
}
