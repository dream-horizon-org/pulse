package org.dreamhorizon.pulseserver.service.spark.models;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SparkJobResponse {

    private String applicationId;
    private String jobRunId;
    private String arn;

    private String jobName;
    /** Echo of submitted main artifact URI. */
    private String entryPoint;
    /** Echo of submitted main class (FQCN), if any. */
    private String mainClass;
    private LocalDateTime submittedAt;
}
