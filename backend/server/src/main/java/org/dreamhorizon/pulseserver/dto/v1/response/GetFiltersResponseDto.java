package org.dreamhorizon.pulseserver.dto.v1.response;

import lombok.Data;
import java.util.List;

@Data
public class GetFiltersResponseDto {
  private List<String> users;
  private List<String> slacks;
  private List<String> statuses;
  private List<String> filterStatuses;
  private List<String> tags;
  private List<String> watermarkUnits;
  private List<String> restartStrategies;
  private List<String> scanModes;
  private List<String> esRetentionPeriods;
  private List<Node> nodes;
  private List<Resource> resources;

  @Data
  public static class Node {
    private String name;
    private String nodeType;
  }

  @Data
  public static class Resource {
    private int id;
    private String name;
  }
}
