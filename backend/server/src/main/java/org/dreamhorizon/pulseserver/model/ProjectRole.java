package org.dreamhorizon.pulseserver.model;

/**
 * Project-level role enumeration.
 * 
 * <p>Roles in descending order of permissions:
 * <ul>
 *   <li>ADMIN: Full project control, manage members and settings</li>
 *   <li>EDITOR: Can modify data and configurations</li>
 *   <li>VIEWER: Read-only access to project data</li>
 * </ul>
 */
public enum ProjectRole {
    /**
     * Project administrator.
     * Can manage project members, modify settings, and perform all editor actions.
     * Cannot delete project (requires tenant admin).
     */
    ADMIN("admin"),
    
    /**
     * Project editor.
     * Can modify project data, configurations, and queries.
     * Cannot manage members or project settings.
     */
    EDITOR("editor"),
    
    /**
     * Project viewer.
     * Read-only access to project data and dashboards.
     * Cannot modify anything.
     */
    VIEWER("viewer");
    
    private final String value;
    
    ProjectRole(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Convert string value to enum (case-insensitive).
     * 
     * @param value String value
     * @return ProjectRole enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static ProjectRole fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Project role cannot be null");
        }
        
        for (ProjectRole role : ProjectRole.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        
        throw new IllegalArgumentException("Invalid project role: " + value + 
            ". Must be one of: admin, editor, viewer");
    }
    
    /**
     * Check if this role can edit project data (ADMIN or EDITOR).
     */
    public boolean canEdit() {
        return this == ADMIN || this == EDITOR;
    }
    
    /**
     * Check if this role has admin privileges (ADMIN only).
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
