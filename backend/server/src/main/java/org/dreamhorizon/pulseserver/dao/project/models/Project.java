package org.dreamhorizon.pulseserver.dao.project.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {
  private Integer id;           // Auto-increment primary key
  private String projectId;     // External identifier (projectName-{uuid})
  private String tenantId;
  private String name;
  private String description;
  private Boolean isActive;
  private String createdBy;
  private String createdAt;
  private String updatedAt;
}

