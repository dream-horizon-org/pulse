package org.dreamhorizon.pulseserver.dao.service.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRow {
  private Long id;
  private String serviceName;
  private String serviceGroup;
  private String displayName;
  private String ownerEmail;
  private String ownerSlackId;
  private String goalertServiceId;
  private String description;
  private Boolean isActive;
  private String createdAt;
  private String updatedAt;
}
