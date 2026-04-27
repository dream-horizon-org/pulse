package org.dreamhorizon.pulseserver.dao.cronjobhistory;

import lombok.Value;

@Value
public class CronJobEnqueueResult {
  long jobId;
  boolean deduplicated;
}
