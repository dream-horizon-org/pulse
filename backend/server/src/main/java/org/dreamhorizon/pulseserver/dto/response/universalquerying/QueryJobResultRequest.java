package org.dreamhorizon.pulseserver.dto.response.universalquerying;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class QueryJobResultRequest {
  @NonNull
  private final String jobId;
  private final String pageToken;
  private final Integer pageSize;

  private QueryJobResultRequest(@NonNull String jobId, String pageToken, Integer pageSize) {
    this.jobId = jobId;
    this.pageToken = pageToken;
    this.pageSize = pageSize;
  }

  public static QueryJobResultRequestBuilder newRequest(@NonNull String jobId) {
    return new QueryJobResultRequestBuilder(jobId);
  }

  public static class QueryJobResultRequestBuilder {
    private final String jobId;
    private String pageToken;
    private Integer pageSize;

    private QueryJobResultRequestBuilder(@NonNull String jobId) {
      this.jobId = jobId;
    }

    public QueryJobResultRequestBuilder pageToken(String pageToken) {
      this.pageToken = pageToken;
      return this;
    }

    public QueryJobResultRequestBuilder pageSize(Integer pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    public QueryJobResultRequest build() {
      return new QueryJobResultRequest(this.jobId, this.pageToken, this.pageSize);
    }
  }
}
