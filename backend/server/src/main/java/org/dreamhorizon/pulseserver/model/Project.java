package org.dreamhorizon.pulseserver.model;

import lombok.Builder;
import lombok.Data;

/**
 * Project entity representing a project within a tenant.
 * Each project has its own API key for SDK authentication and dedicated ClickHouse user.
 * Project-level permissions are managed in OpenFGA.
 */
@Data
@Builder
public class Project {
    private Long id;
    private String projectId;       // Format: proj-{uuid}
    private String tenantId;        // Parent tenant
    private String name;            // Project display name
    private String description;     // Project description
    private String apiKey;          // Format: pulse_{projectId}_sk_{random}
    private Boolean isActive;       // Project active status
    private String createdBy;       // User ID of creator
    private String createdAt;
    private String updatedAt;
}
