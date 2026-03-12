package org.dreamhorizon.pulseserver.resources.v1.members.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing a list of members.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberListResponse {
    private List<MemberResponse> members;  // List of members
    private Integer totalCount;            // Total count of members
}
