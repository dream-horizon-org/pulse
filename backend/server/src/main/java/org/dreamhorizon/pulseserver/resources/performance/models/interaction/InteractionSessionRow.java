package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionSessionRow {
    private String traceId;
    private String spanId;
    private String timestamp;
    private Long durationMs;
    private String statusCode;
    private String platform;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String userId;
    private String sessionId;
}
