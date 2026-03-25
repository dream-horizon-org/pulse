package org.dreamhorizon.pulseserver.service.spark.models;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SparkJobRequest {
    
    private String jobName;
    
    private String mainClass;           // Spark entry point
    
    private List<String> arguments;     // Job arguments
    private String sparkConfig;         // Spark parameters
    private Integer timeoutMinutes;
    private Map<String, String> tags;
    private String mode;                // "BATCH" or "STREAMING"
}