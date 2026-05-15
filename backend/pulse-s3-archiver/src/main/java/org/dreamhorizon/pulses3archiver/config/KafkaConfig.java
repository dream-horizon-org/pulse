package org.dreamhorizon.pulses3archiver.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaConfig {
  private String bootstrapServers = "pulse-kafka-01.pulse.local:9092,pulse-kafka-02.pulse.local:9092";
  private String groupId = "pulse-s3-archiver";
  private String clientId = "pulse-s3-archiver";
  private String autoOffsetReset = "earliest";
  private int maxPollRecords = 2000;
  private int fetchMinBytes = 1048576;
  private int fetchMaxWaitMs = 500;
  private String topicTraces = "pulse.traces";
  private String topicLogs = "pulse.logs";
  private String topicMetrics = "pulse.metrics";
}
