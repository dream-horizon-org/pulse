package org.dreamhorizon.pulseserver.resources.v1.projects.members;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.members.models.AddMemberRequest;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberListResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.UpdateMemberRoleRequest;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.ProjectMemberService;

import static org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId;

/**
 * REST resource for project member management.
 *
 * <p>This resource layer follows clean architecture principles:
 * <ul>
 *   <li>No business logic - delegates everything to service layer</li>
 *   <li>Only handles: request/response transformation, parameter extraction</li>
 *   <li>Uses AuthenticationUtil for consistent token handling</li>
 *   <li>Authorization checks are in service layer</li>
 * </ul>
 */
@Slf4j
@Path("/v1/projects/{projectId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectMemberResource {
    
    private final ProjectMemberService projectMemberService;
    private final OpenFgaService openFgaService;
    
    /**
     * Add a member to a project.
     * Authorization and business logic handled in ProjectMemberService.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param request Member details (email, role)
     * @return Added member information
     */
    @POST
    @RequiresPermission("can_edit")
    public CompletionStage<Response<MemberResponse>> addMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            AddMemberRequest request) {
        
        String userId = extractUserId(authorization);
        
        log.info("Adding member to project: email={}, project={}, role={}, addedBy={}", 
            request.getEmail(), projectId, request.getRole(), userId);
        
        return projectMemberService.addMemberToProject(
                projectId,
                request.getEmail(),
                request.getRole(),
                userId
            )
            .flatMap(user -> openFgaService.getUserRoleInProject(user.getUserId(), projectId)
                .map(roleOpt -> MemberResponse.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .role(roleOpt.orElse(request.getRole()))
                    .status(user.getStatus())
                    .lastLoginAt(user.getLastLoginAt())
                    .build()))
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * List all members of a project.
     * Requires project member role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @return List of project members
     */
    @GET
    @RequiresPermission("can_view")
    public CompletionStage<Response<MemberListResponse>> listMembers(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId) {
        
        String userId = extractUserId(authorization);
        
        log.debug("Listing project members: project={}, requestedBy={}", projectId, userId);
        
        return projectMemberService.listProjectMembers(projectId, userId)
            .flatMap(members -> enrichMembersWithRoles(members, projectId))
            .map(enrichedMembers -> MemberListResponse.builder()
                .members(enrichedMembers)
                .totalCount(enrichedMembers.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Remove a member from a project.
     * Requires project admin role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param targetUserId User ID to remove
     * @return Success response
     */
    @DELETE
    @Path("/{userId}")
    @RequiresPermission("can_edit")
    public CompletionStage<Response<Object>> removeMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            @PathParam("userId") String targetUserId) {
        
        String removerId = extractUserId(authorization);
        
        log.info("Removing member from project: user={}, project={}, removedBy={}", 
            targetUserId, projectId, removerId);
        
        return projectMemberService.removeMemberFromProject(projectId, targetUserId, removerId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Update a member's role in a project.
     * Requires project admin role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param targetUserId User ID to update
     * @param request New role
     * @return Success response
     */
    @PATCH
    @Path("/{userId}")
    @RequiresPermission("can_edit")
    public CompletionStage<Response<Object>> updateMemberRole(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            @PathParam("userId") String targetUserId,
            UpdateMemberRoleRequest request) {
        
        String updaterId = extractUserId(authorization);
        
        log.info("Updating project member role: user={}, project={}, newRole={}, updatedBy={}", 
            targetUserId, projectId, request.getNewRole(), updaterId);
        
        return projectMemberService.updateMemberRole(projectId, targetUserId, request.getNewRole(), updaterId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Leave a project (self-removal).
     * User removes themselves from the project.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @return Success response
     */
    @DELETE
    @Path("/leave")
    @RequiresPermission("can_view")
    public CompletionStage<Response<Object>> leaveProject(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId) {
        
        String userId = extractUserId(authorization);
        
        log.info("User leaving project: user={}, project={}", userId, projectId);
        
        return projectMemberService.leaveProject(projectId, userId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Enrich members list with roles from OpenFGA.
     * Helper method to add role information to member responses.
     */
    private Single<List<MemberResponse>> enrichMembersWithRoles(List<User> members, String projectId) {
        if (members.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        List<Single<MemberResponse>> enrichments = new ArrayList<>();
        for (User member : members) {
            Single<MemberResponse> enriched = openFgaService.getUserRoleInProject(member.getUserId(), projectId)
                .map(roleOpt -> MemberResponse.builder()
                    .userId(member.getUserId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .role(roleOpt.orElse("viewer"))
                    .status(member.getStatus())
                    .lastLoginAt(member.getLastLoginAt())
                    .build());
            enrichments.add(enriched);
        }
        
        return Single.zip(enrichments, results -> {
            List<MemberResponse> responses = new ArrayList<>();
            for (Object result : results) {
                responses.add((MemberResponse) result);
            }
            return responses;
        });
    }
}
