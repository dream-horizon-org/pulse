package org.dreamhorizon.pulseserver.model;

/**
 * User account status enumeration.
 * 
 * <p>Status lifecycle:
 * <ul>
 *   <li>PENDING: User invited by admin but hasn't logged in yet</li>
 *   <li>ACTIVE: User has completed first login and account is active</li>
 *   <li>SUSPENDED: User account temporarily disabled by admin</li>
 *   <li>DELETED: User account marked for deletion</li>
 * </ul>
 */
public enum UserStatus {
    /**
     * User has been invited/created but never logged in.
     * Transitions to ACTIVE on first successful login.
     */
    PENDING("pending"),
    
    /**
     * User account is active and can access the system.
     */
    ACTIVE("active"),
    
    /**
     * User account temporarily suspended by admin.
     * User cannot login but data is preserved.
     */
    SUSPENDED("suspended"),
    
    /**
     * User account marked for deletion.
     * Account data may be removed after grace period.
     */
    DELETED("deleted");
    
    private final String value;
    
    UserStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Convert string value to enum (case-insensitive).
     * 
     * @param value String value
     * @return UserStatus enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static UserStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("User status cannot be null");
        }
        
        for (UserStatus status : UserStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        
        throw new IllegalArgumentException("Invalid user status: " + value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
