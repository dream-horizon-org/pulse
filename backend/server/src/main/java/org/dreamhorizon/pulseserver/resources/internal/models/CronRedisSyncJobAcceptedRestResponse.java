package org.dreamhorizon.pulseserver.resources.internal.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ack for async Kong Redis materialization jobs (HTTP 202). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronRedisSyncJobAcceptedRestResponse {
  private long jobId;
  private boolean deduplicated;
  private String jobType;
}
