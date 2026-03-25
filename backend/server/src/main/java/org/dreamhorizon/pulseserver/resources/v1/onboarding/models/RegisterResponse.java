package org.dreamhorizon.pulseserver.resources.v1.onboarding.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("tenant_id")
    private String tenantId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("full_name")
    private String fullName;
    
    @JsonProperty("organization_name")
    private String organizationName;
    
    @JsonProperty("message")
    private String message;
}
