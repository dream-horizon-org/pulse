package org.dreamhorizon.pulseserver.resources.v1.tenants;

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
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.TenantMemberService;

import static org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.util.CompletableFutureUtils;

/**
 * REST resource for tenant member management.
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
@Path("/v1/tenants/{tenantId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantMemberResource {
    
    private final TenantMemberService tenantMemberService;
    private final OpenFgaService openFgaService;
    
    /**
     * Add a user to a tenant.
     * Authorization and business logic handled in TenantMemberService.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param request Member details (email, role)
     * @return Added member information
     */
    @POST
    public CompletionStage<Response<MemberResponse>> addMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            AddMemberRequest request) {
        
        String userId = extractUserId(authorization);
        
        log.info("Adding member to tenant: email={}, tenant={}, role={}, addedBy={}", 
            request.getEmail(), tenantId, request.getRole(), userId);
        
        return tenantMemberService.addUserToTenant(
                tenantId,
                request.getEmail(),
                request.getRole(),
                userId
            )
            .flatMap(user ->
                // Get role from OpenFGA for response
                openFgaService.getUserTenantRole(user.getUserId(), tenantId)
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
     * List all members of a tenant.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @return List of tenant members with roles
     */
    @GET
    public CompletionStage<Response<MemberListResponse>> listMembers(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId) {
        
        String userId = extractUserId(authorization);
        
        log.debug("Listing tenant members: tenant={}, requestedBy={}", tenantId, userId);
        
        return tenantMemberService.listTenantMembers(tenantId, userId)
            .flatMap(members -> enrichMembersWithRoles(members, tenantId))
            .map(enrichedMembers -> MemberListResponse.builder()
                .members(enrichedMembers)
                .totalCount(enrichedMembers.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Remove a user from a tenant (cascades to all projects).
     * Authorization checked in service layer.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param targetUserId User ID to remove
     * @return Success response
     */
    @DELETE
    @Path("/{userId}")
    public CompletionStage<Response<Object>> removeMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            @PathParam("userId") String targetUserId) {
        
        String removerId = extractUserId(authorization);
        
        log.info("Removing member from tenant: user={}, tenant={}, removedBy={}", 
            targetUserId, tenantId, removerId);
        
        return tenantMemberService.removeUserFromTenant(tenantId, targetUserId, removerId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Update a member's role in a tenant.
     * Authorization and self-role-change prevention in service layer.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param targetUserId User ID to update
     * @param request New role
     * @return Success response
     */
    @PATCH
    @Path("/{userId}")
    public CompletionStage<Response<Object>> updateMemberRole(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            @PathParam("userId") String targetUserId,
            UpdateMemberRoleRequest request) {
        
        String updaterId = extractUserId(authorization);
        
        log.info("Updating tenant member role: user={}, tenant={}, newRole={}, updatedBy={}", 
            targetUserId, tenantId, request.getNewRole(), updaterId);
        
        return tenantMemberService.updateTenantRole(
                tenantId,
                targetUserId,
                request.getNewRole(),
                updaterId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Leave a tenant (self-removal).
     * User removes themselves from the tenant.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @return Success response
     */
    @DELETE
    @Path("/leave")
    public CompletionStage<Response<Object>> leaveTenant(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId) {
        
        String userId = extractUserId(authorization);
        
        log.info("User leaving tenant: user={}, tenant={}", userId, tenantId);
        
        return tenantMemberService.leaveTenant(tenantId, userId)
            .toSingle(() -> Collections.emptyMap())
            .map(obj -> (Object) obj)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Enrich members list with roles from OpenFGA.
     * Helper method to add role information to member responses.
     */
    private Single<List<MemberResponse>> enrichMembersWithRoles(List<User> members, String tenantId) {
        if (members.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        List<Single<MemberResponse>> enrichments = new ArrayList<>();
        for (User member : members) {
            Single<MemberResponse> enriched = openFgaService.getUserTenantRole(member.getUserId(), tenantId)
                .map(roleOpt -> MemberResponse.builder()
                    .userId(member.getUserId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .role(roleOpt.orElse("member"))
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
