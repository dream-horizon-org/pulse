package org.dreamhorizon.pulseserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for user profile information.
 * Includes user details and tenant/project roles.
 */
@Data
@Builder
public class UserProfileDto {
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("tenant_role")
    private String tenantRole;  // admin or member at tenant level
    
    @JsonProperty("is_active")
    private Boolean isActive;
}
