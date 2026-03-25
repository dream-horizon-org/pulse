package org.dreamhorizon.pulseserver.model;

/**
 * Tenant-level role enumeration.
 * 
 * <p>Roles in descending order of permissions:
 * <ul>
 *   <li>OWNER: Full control, can delete tenant, manage billing</li>
 *   <li>ADMIN: Manage members, projects, settings (cannot delete tenant)</li>
 *   <li>MEMBER: Basic access, can view tenant and access assigned projects</li>
 * </ul>
 */
public enum TenantRole {
    /**
     * Tenant owner with full control.
     * Can delete tenant, manage billing, and perform all admin actions.
     * Typically the user who created the tenant.
     */
    OWNER("owner"),
    
    /**
     * Tenant administrator.
     * Can manage members, create projects, and modify settings.
     * Cannot delete tenant or manage billing.
     */
    ADMIN("admin"),
    
    /**
     * Regular tenant member.
     * Can view tenant information and access assigned projects.
     * Cannot manage members or tenant settings.
     */
    MEMBER("member");
    
    private final String value;
    
    TenantRole(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Convert string value to enum (case-insensitive).
     * 
     * @param value String value
     * @return TenantRole enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static TenantRole fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Tenant role cannot be null");
        }
        
        for (TenantRole role : TenantRole.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        
        throw new IllegalArgumentException("Invalid tenant role: " + value + 
            ". Must be one of: owner, admin, member");
    }
    
    /**
     * Check if this role has admin privileges (OWNER or ADMIN).
     */
    public boolean isAdmin() {
        return this == OWNER || this == ADMIN;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
