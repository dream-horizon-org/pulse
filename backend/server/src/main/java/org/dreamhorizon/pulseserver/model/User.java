package org.dreamhorizon.pulseserver.model;

import lombok.Builder;
import lombok.Data;

/**
 * User entity representing user profile information.
 * User authentication is handled via Google OAuth (Firebase).
 * User-to-tenant and user-to-project relationships are managed in OpenFGA.
 */
@Data
@Builder(toBuilder = true)
public class User {
    private Long id;
    private String userId;          // Format: user-{uuid}
    private String email;           // From Google OAuth
    private String name;            // Display name
    private String status;          // pending, active, suspended
    private String firebaseUid;     // Firebase user ID for authentication
    private String lastLoginAt;     // Last login timestamp
    private Boolean isActive;       // Account active status
    private String createdAt;
    private String updatedAt;
}
