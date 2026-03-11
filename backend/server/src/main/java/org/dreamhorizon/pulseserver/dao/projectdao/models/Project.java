package org.dreamhorizon.pulseserver.dao.projectdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a project within a tenant.
 * Projects are the primary unit of organization for resources like alerts, SDK configs, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

  /**
   * Unique identifier for the project (UUID).
   */
  private String projectId;

  /**
   * The tenant this project belongs to.
   */
  private String tenantId;

  /**
   * Display name of the project.
   */
  private String name;

  /**
   * Optional description of the project.
   */
  private String description;

  /**
   * Optional slug/identifier for the project (URL-friendly).
   */
  private String slug;

  /**
   * Whether the project is active.
   */
  private Boolean isActive;

  /**
   * Timestamp when the project was created (ISO 8601 format).
   */
  private String createdAt;

  /**
   * Timestamp when the project was last updated (ISO 8601 format).
   */
  private String updatedAt;

  /**
   * User ID of the project creator.
   */
  private String createdBy;
}

