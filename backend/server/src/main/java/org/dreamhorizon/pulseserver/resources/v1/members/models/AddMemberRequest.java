package org.dreamhorizon.pulseserver.resources.v1.members.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to add member(s) to a tenant or project.
 * Supports both single and bulk invite operations via the emails list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    private List<String> emails; // Email addresses (single or multiple)
    private String role;          // Role: tenant (admin/member), project (admin/editor/viewer)
}
