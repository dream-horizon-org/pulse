package org.dreamhorizon.pulseserver.resources.v1.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.AssignInternalViewerRequest;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.SuperAdminsListResponse;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;

/**
 * Grant, revoke, and list OpenFGA internal_viewer users on {@code system:pulse}. Caller must be a superadmin.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/admin/internal-viewers")
@Produces(MediaType.APPLICATION_JSON)
public class InternalViewerResource {

  private final Provider<OpenFgaService> openFgaServiceProvider;
  private final JwtService jwtService;
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

  private static String bearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw ServiceError.UNAUTHORISED.getCustomException("Missing or invalid Authorization header");
    }
    return authorization.substring("Bearer ".length()).trim();
  }

  private String verifiedCallerUserId(String authorization) {
    String token = bearerToken(authorization);
    final Claims claims;
    try {
      claims = jwtService.verifyToken(token);
    } catch (ExpiredJwtException e) {
      log.debug("Expired JWT for internal-viewer admin resource");
      throw ServiceError.UNAUTHORISED.getCustomException("Token expired", "Please log in again");
    } catch (JwtException e) {
      log.debug("Invalid JWT for internal-viewer admin resource: {}", e.getMessage());
      throw ServiceError.UNAUTHORISED.getCustomException("Invalid authentication token", "Please log in again");
    } catch (Exception e) {
      log.error("Unexpected error verifying JWT for internal-viewer admin resource", e);
      throw ServiceError.UNAUTHORISED.getCustomException("Authentication failed", "Unable to verify token");
    }
    String userId = claims.getSubject();
    if (userId == null || userId.isBlank()) {
      throw ServiceError.UNAUTHORISED.getCustomException(
          "Invalid authentication token", "Token subject is missing");
    }
    return userId;
  }

  @GET
  public CompletionStage<Response<SuperAdminsListResponse>> list(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = verifiedCallerUserId(authorization);
      return fga.isSuperAdmin(callerUserId)
          .flatMap(isSa -> {
            if (!Boolean.TRUE.equals(isSa)) {
              return Single.error(new ForbiddenOperationException("Only superadmins can list internal viewers"));
            }
            return fga.getInternalViewers();
          })
          .flatMap(set -> AdminRoleListResponseFactory.build(set, userService));
    }).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SuperAdminsListResponse>> assign(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      AssignInternalViewerRequest body) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = verifiedCallerUserId(authorization);

      if (body == null) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("userId or email is required"));
      }

      return AdminRoleListResponseFactory.resolveTargetUserId(
              body.getUserId(), body.getEmail(), userService)
          .flatMap(
              targetUserId ->
                  fga.isSuperAdmin(callerUserId)
                      .flatMap(
                          isSa -> {
                            if (!Boolean.TRUE.equals(isSa)) {
                              return Single.error(
                                  new ForbiddenOperationException(
                                      "Only superadmins can assign internal_viewer"));
                            }
                            log.info(
                                "AUDIT internal_viewer_grant: caller={} target={} ts={}",
                                callerUserId,
                                targetUserId,
                                Instant.now());
                            return fga.assignInternalViewerRole(targetUserId)
                                .andThen(fga.getInternalViewers());
                          })
                      .flatMap(set -> AdminRoleListResponseFactory.build(set, userService)));
    }).to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{userId}")
  public CompletionStage<Response<SuperAdminsListResponse>> revoke(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @PathParam("userId") String targetUserId) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = verifiedCallerUserId(authorization);

      if (StringUtils.isBlank(targetUserId)) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_PATH_PARAMETERS.getCustomException(
                "userId path parameter is required"));
      }

      return fga.isSuperAdmin(callerUserId)
          .flatMap(isSa -> {
            if (!Boolean.TRUE.equals(isSa)) {
              return Single.error(new ForbiddenOperationException("Only superadmins can revoke internal_viewer"));
            }
            return fga.getInternalViewers();
          })
          .flatMap(viewers -> {
            if (!viewers.contains(targetUserId)) {
              return Single.error(ServiceError.NOT_FOUND.getCustomException("User is not an internal viewer"));
            }
            log.info(
                "AUDIT internal_viewer_revoke: caller={} target={} ts={}",
                callerUserId,
                targetUserId,
                Instant.now());
            return fga.revokeInternalViewerRole(targetUserId).andThen(fga.getInternalViewers());
          })
          .flatMap(set -> AdminRoleListResponseFactory.build(set, userService));
    }).to(RestResponse.jaxrsRestHandler());
  }
}
