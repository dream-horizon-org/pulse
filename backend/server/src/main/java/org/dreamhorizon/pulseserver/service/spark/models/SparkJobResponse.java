package org.dreamhorizon.pulseserver.service.spark.models;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response model for Spark job submission.
 * Mirrors EMR StartJobRunResponse with additional metadata for convenience.
 */
@Getter
@Builder
public class SparkJobResponse {
    
    private String applicationId;       // EMR application ID
    private String jobRunId;            // EMR job run ID
    private String arn;                 // EMR job run ARN
    
    // Additional metadata for convenience
    private String jobName;
    private String mainClass;
    private LocalDateTime submittedAt;
}