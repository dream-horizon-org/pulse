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

  private QueryConfiguration(
      String query,
      boolean useLegacySql,
      Integer timeoutMs,
      JobCreationMode jobCreationMode,
      String tenantId
  ) {
    this.query = query;
    this.useLegacySql = useLegacySql;
    this.timeoutMs = timeoutMs;
    this.jobCreationMode = jobCreationMode;
    this.tenantId = tenantId;
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

    public QueryConfiguration build() {
      // TODO: Find better way to handle defaults
      if (timeoutMs == null) {
        timeoutMs = 60000;
      }

      return new QueryConfiguration(this.query, this.useLegacySql, this.timeoutMs, jobCreationMode, tenantId);
    }
  }
}
