package org.dreamhorizon.pulseserver.service.spark.models;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SparkJobRequest {

    private String jobName;

    /** Main artifact: typically {@code s3://bucket/key/app.jar} or a PySpark script URI. */
    private String entryPoint;

    /** Fully qualified main class for Java/Scala; omit for PySpark if not used. */
    private String mainClass;

    private List<String> arguments;

    /**
     * Extra {@code spark-submit} flags, e.g. {@code --conf k=v --jars s3://.../lib.jar}. Do not pass
     * {@code --class} here; set {@code mainClass} instead (enforced in {@code SparkJobServiceImpl}).
     */
    private String sparkSubmitParameters;

    private Long timeoutMinutes;
    private Map<String, String> tags;
}
