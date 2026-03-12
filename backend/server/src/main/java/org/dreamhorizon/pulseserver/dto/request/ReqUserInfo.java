package org.dreamhorizon.pulseserver.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing user information extracted from JWT claims.
 * Used to pass authenticated user details from controllers to services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUserInfo {
    private String userId;
    private String email;
    private String name;
}
