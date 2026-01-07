package org.dreamhorizon.pulseserver.errorgrouping.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
public class StackTraceEvent {
  // --- Core ---
  @JsonProperty("Timestamp")
  private String timestamp;

  @JsonProperty("PulseType")
  private String pulseType;

  @JsonProperty("Title")
  private String title;

  // --- Exception details ---
  @JsonProperty("ExceptionStackTrace")
  private String exceptionStackTrace;

  @JsonProperty("ExceptionStackTraceRaw")
  private String exceptionStackTraceRaw;

  @JsonProperty("ExceptionMessage")
  private String exceptionMessage;

  @JsonProperty("ExceptionType")
  private String exceptionType;

  // --- App/session context ---
  @JsonProperty("Interactions")
  private List<String> interactions;

  @JsonProperty("ScreenName")
  private String screenName;

  @JsonProperty("UserId")
  private String userId;

  @JsonProperty("SessionId")
  private String sessionId;

  // --- Device/app metadata ---
  @JsonProperty("Platform")
  private String platform;

  @JsonProperty("OsVersion")
  private String osVersion;

  @JsonProperty("DeviceModel")
  private String deviceModel;

  @JsonProperty("AppVersionCode")
  private String appVersionCode;

  @JsonProperty("AppVersion")
  private String appVersion;

  @JsonProperty("SdkVersion")
  private String sdkVersion;

  // --- Tracing ---
  @JsonProperty("TraceId")
  private String traceId;

  @JsonProperty("SpanId")
  private String spanId;

  // --- Grouping keys ---
  @JsonProperty("GroupId")
  private String groupId;

  @JsonProperty("Signature")
  private String signature;

  @JsonProperty("Fingerprint")
  private String fingerprint;

  @JsonProperty("ScopeAttributes")
  private Map<String, String> scopeAttributes;

  @JsonProperty("LogAttributes")
  private Map<String, String> logAttributes;

  @JsonProperty("ResourceAttributes")
  private Map<String, String> resourceAttributes;
}
