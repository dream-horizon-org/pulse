package org.dreamhorizon.pulseserver.resources.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllConfigdetails {
  private List<Configdetails> configDetails;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Configdetails {
    private long version;
    private boolean isactive;
    private String description;
    private String createdBy;
    private String createdAt;
  }
}
