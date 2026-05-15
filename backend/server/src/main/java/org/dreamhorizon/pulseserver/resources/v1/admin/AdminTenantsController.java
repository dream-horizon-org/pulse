package org.dreamhorizon.pulseserver.resources.v1.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.HeaderParam;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.CreateAdminTenantRequest;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.CreateAdminTenantResponse;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;

/**
 * Admin endpoint for atomic tenant + project creation.
 * Reuses {@code TenantService.createTenantWithProject} — the same shared provisioning path
 * used by the onboarding flow — so both flows stay in parity.
 *
 * <p>Not annotated with {@code @RequiresPermission}; superadmin / internal_viewer check is explicit here.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/admin/tenants")
@Produces(MediaType.APPLICATION_JSON)
public class AdminTenantsController {

  private final TenantService tenantService;
  private final JwtService jwtService;
  private final Provider<OpenFgaService> openFgaServiceProvider;
  private final UserService userService;

  private OpenFgaService requireOpenFga() {
    OpenFgaService fga = openFgaServiceProvider.get();
    if (fga == null || !fga.isEnabled()) {
      throw ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
          "OpenFGA is not available",
          "OpenFGA is disabled or not initialized",
          503);
    }
    return fga;
  }

  private String verifiedCallerUserId(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw ServiceError.UNAUTHORISED.getCustomException("Missing or invalid Authorization header");
    }
    String token = authorization.substring("Bearer ".length()).trim();
    if (!jwtService.isAccessToken(token)) {
      throw ServiceError.UNAUTHORISED.getCustomException("Invalid token type. Expected access token.");
    }
    final Claims claims;
    try {
      claims = jwtService.verifyToken(token);
    } catch (ExpiredJwtException e) {
      log.debug("Expired JWT for admin tenants endpoint");
      throw ServiceError.UNAUTHORISED.getCustomException("Token expired", "Please log in again");
    } catch (JwtException e) {
      log.debug("Invalid JWT for admin tenants endpoint: {}", e.getMessage());
      throw ServiceError.UNAUTHORISED.getCustomException("Invalid authentication token", "Please log in again");
    } catch (Exception e) {
      log.error("Unexpected error verifying JWT for admin tenants endpoint", e);
      throw ServiceError.UNAUTHORISED.getCustomException("Authentication failed", "Unable to verify token");
    }
    String userId = claims.getSubject();
    if (userId == null || userId.isBlank()) {
      throw ServiceError.UNAUTHORISED.getCustomException("Invalid authentication token", "Token subject is missing");
    }
    return userId;
  }

  /**
   * Atomically creates a tenant + first project.
   * Caller must be superadmin or internal_viewer. Caller becomes the tenant/project admin.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateAdminTenantResponse>> createTenantWithProject(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      CreateAdminTenantRequest body) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerId = verifiedCallerUserId(authorization);

      if (body == null || body.getTenantName() == null || body.getTenantName().isBlank()) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("tenantName is required"));
      }
      if (body.getProjectName() == null || body.getProjectName().isBlank()) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("projectName is required"));
      }

      return Single.zip(
              fga.isSuperAdmin(callerId),
              fga.isInternalViewer(callerId),
              (isSuperAdmin, isInternalViewer) -> Boolean.TRUE.equals(isSuperAdmin) || Boolean.TRUE.equals(isInternalViewer))
          .flatMap(isSystemRole -> {
            if (!Boolean.TRUE.equals(isSystemRole)) {
              return Single.error(
                  new ForbiddenOperationException("Only superadmin or internal_viewer can use this endpoint"));
            }

            return userService.getUserById(callerId)
                .flatMap(user -> {
                  ReqUserInfo ownerInfo = ReqUserInfo.builder()
                      .userId(callerId)
                      .email(user.getEmail())
                      .name(user.getName())
                      .build();

                  log.info("AUDIT admin_create_tenant: caller={} tenantName={} projectName={}",
                      callerId, body.getTenantName(), body.getProjectName());

                  return tenantService.createTenantWithProject(
                      ownerInfo,
                      body.getTenantName(),
                      body.getProjectName(),
                      null,
                      body.getProjectDescription())
                      .map(result -> {
                        CreateAdminTenantResponse response = new CreateAdminTenantResponse();
                        response.setTenantId(result.getTenantId());
                        response.setProjectId(result.getProjectId());
                        response.setApiKey(result.getRawApiKey());
                        return response;
                      });
                });
          });
    }).to(RestResponse.jaxrsRestHandler());
  }
}
