package org.dreamhorizon.pulseserver.resources.configs.models;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Config {
  private String version;
  private ConfigData configData;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ConfigData {
    private List<Map<String, Object>> filtersConfig;
    private Map<String, Object> samplingConfig;
    private Map<String, Object> signals;
    private Map<String, Object> interaction;
    private List<Map<String, Object>> featureConfigs;
  }
}
