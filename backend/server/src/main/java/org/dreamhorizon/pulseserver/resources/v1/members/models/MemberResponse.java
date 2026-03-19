package org.dreamhorizon.pulseserver.resources.v1.members.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing member information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    private String userId;           // User ID
    private String email;            // User email
    private String name;             // User name
    private String role;             // Role in tenant or project
    private String status;           // pending, active, suspended
    private String lastLoginAt;      // Last login timestamp
}
