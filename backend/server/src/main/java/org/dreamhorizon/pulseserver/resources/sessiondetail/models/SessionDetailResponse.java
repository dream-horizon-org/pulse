package org.dreamhorizon.pulseserver.resources.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDetailResponse {

  // --- Core ---
  private String sessionId;
  private String userId;
  private boolean isAnonymous;
  private String startTime;
  private String endTime;
  private long duration;
  private String platform;
  private String device;
  private String osVersion;
  private String appVersion;
  private String geography;
  private double quality;
  private List<String> journey;

  // --- Interactions (aggregated by name) ---
  private List<Interaction> interactions;

  // --- Network ---
  private List<NetworkRequest> networkRequests;

  // --- Events (optional via include) ---
  private List<Event> events;

  // --- Exceptions (optional via include=exceptions) ---
  private List<SessionException> exceptions;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Interaction {
    private String interactionName;
    private long successCount;
    private long failureCount;
    private double durationMs;
    private String status;
    private double apdexScore;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NetworkRequest {
    private String timestamp;
    private long durationNs;
    private String method;
    private String url;
    private String status;
    private String target;
    private String traceId;
    private String spanId;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Event {
    private String timestamp;
    private EventType eventType;
    private String description;
    private long durationNs;
    private String traceId;
    private String spanId;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SessionException {
    private String timestamp;
    private String pulseType;
    private String title;
    private String exceptionStackTrace;
    private String traceId;
    private String spanId;
  }
}
