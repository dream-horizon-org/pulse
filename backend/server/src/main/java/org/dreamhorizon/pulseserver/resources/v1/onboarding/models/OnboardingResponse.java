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
public class OnboardingResponse {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("tenantId")
    private String tenantId;
    
    @JsonProperty("tenantName")
    private String tenantName;
    
    @JsonProperty("tier")
    private String tier;  // Always "free" for new tenants
    
    @JsonProperty("projectId")
    private String projectId;
    
    @JsonProperty("projectName")
    private String projectName;
    
    @JsonProperty("projectApiKey")
    private String projectApiKey;
    
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("refreshToken")
    private String refreshToken;
    
    @JsonProperty("tokenType")
    private String tokenType;
    
    @JsonProperty("expiresIn")
    private Integer expiresIn;
    
    @JsonProperty("redirectTo")
    private String redirectTo;
}
