package org.dreamhorizon.pulseserver.service.spark.models;

import lombok.Builder;
import lombok.Getter;

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
    /** ISO-8601 instant when the job was submitted (e.g. {@code Instant.now().toString()}). */
    private String submittedAt;
}
