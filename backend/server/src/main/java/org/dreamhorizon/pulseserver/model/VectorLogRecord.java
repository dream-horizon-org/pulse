package org.dreamhorizon.pulseserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VectorLogRecord {
  @JsonProperty("attributes")
  private Map<String, Object> attributes;

  @JsonProperty("dropped_attributes_count")
  private Integer droppedAttributesCount;

  @JsonProperty("message")
  private String message;

  @JsonProperty("observed_timestamp")
  private String observedTimestamp;

  @JsonProperty("resources")
  private Map<String, Object> resources;

  @JsonProperty("scope")
  private Map<String, Object> scope;

  @JsonProperty("source_type")
  private String sourceType;

  @JsonProperty("timestamp")
  private String timestamp;

  @JsonProperty("trace_id")
  private String traceId;

  @JsonProperty("span_id")
  private String spanId;
}

