package org.dreamhorizon.pulseserver.model;

/**
 * Login status enumeration for authentication responses.
 * 
 * <p>Represents the outcome of a login attempt and determines
 * what actions the user needs to take next.
 */
public enum LoginStatus {
    /**
     * Login successful - user is authenticated and has access to projects.
     * User should be redirected to the application dashboard.
     */
    SUCCESS("success"),
    
    /**
     * User is authenticated but needs to complete onboarding.
     * User has no projects/tenants and must create or join one.
     */
    NEEDS_ONBOARDING("needs_onboarding"),
    
    /**
     * Login requires additional verification (e.g., 2FA).
     * User must complete verification step before access is granted.
     */
    REQUIRES_VERIFICATION("requires_verification"),
    
    /**
     * Authentication successful but account is pending activation.
     * User was invited but hasn't completed first-time setup.
     */
    PENDING_ACTIVATION("pending_activation");
    
    private final String value;
    
    LoginStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Convert string value to enum (case-insensitive).
     * 
     * @param value String value
     * @return LoginStatus enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static LoginStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Login status cannot be null");
        }
        
        for (LoginStatus status : LoginStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        
        throw new IllegalArgumentException("Invalid login status: " + value + 
            ". Must be one of: success, needs_onboarding, requires_verification, pending_activation");
    }
    
    /**
     * Check if this status indicates successful authentication.
     */
    public boolean isAuthenticated() {
        return this == SUCCESS;
    }
    
    /**
     * Check if this status requires user action.
     */
    public boolean requiresAction() {
        return this == NEEDS_ONBOARDING || this == REQUIRES_VERIFICATION || this == PENDING_ACTIVATION;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
