package org.dreamhorizon.pulseserver.resources.v1.members.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to add a member to a tenant or project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    private String email;  // User's email address
    private String role;   // Role: tenant (admin/member), project (admin/editor/viewer)
}
