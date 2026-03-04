package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.model.LoginStatus;

/**
 * Login response DTO.
 * 
 * <p>Returns authentication status and tokens (if successful).
 * The {@code status} field determines what the client should do next:
 * <ul>
 *   <li>SUCCESS: User is authenticated, use tokens to access API</li>
 *   <li>NEEDS_ONBOARDING: User must create/join a tenant first</li>
 *   <li>REQUIRES_VERIFICATION: Additional verification step needed</li>
 * </ul>
 * 
 * <p><b>Design Note:</b>
 * Both {@code status} (enum) and {@code needsOnboarding} (boolean) fields are maintained.
 * While this creates redundancy, it ensures:
 * <ul>
 *   <li>Type safety on the backend (via enum)</li>
 *   <li>Backward compatibility with existing frontend code (via boolean)</li>
 *   <li>Clear migration path for future refactoring</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * Login status indicating the outcome and next action.
     * This is the primary field that provides type safety.
     */
    @JsonProperty("status")
    private LoginStatus status;
    
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("refreshToken")
    private String refreshToken;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("tenantId")
    private String tenantId;
    
    @JsonProperty("tenantRole")
    private String tenantRole;
    
    @JsonProperty("tier")
    private String tier;  // "free" or "enterprise"
    
    @JsonProperty("needsOnboarding")
    private Boolean needsOnboarding;
    
    @JsonProperty("tokenType")
    private String tokenType;
    
    @JsonProperty("expiresIn")
    private Integer expiresIn;
}
