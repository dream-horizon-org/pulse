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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.GrantSuperAdminRequest;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.SuperAdminsListResponse;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.util.JwtUtils;

/**
 * Grant, revoke, and list OpenFGA superadmins on {@code system:pulse}. Caller must already be a superadmin.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/admin/superadmins")
@Produces(MediaType.APPLICATION_JSON)
public class SuperAdminResource {

  private final Provider<OpenFgaService> openFgaServiceProvider;

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
    return authorization.substring("Bearer ".length());
  }

  @GET
  public CompletionStage<Response<SuperAdminsListResponse>> list(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = JwtUtils.extractUserId(bearerToken(authorization));
      return fga.isSuperAdmin(callerUserId)
          .flatMap(isSa -> {
            if (!Boolean.TRUE.equals(isSa)) {
              return Single.error(new ForbiddenOperationException("Only superadmins can list superadmins"));
            }
            return fga.getSuperAdmins();
          })
          .map(set -> {
            List<String> ids = new ArrayList<>(set);
            ids.sort(String::compareTo);
            return SuperAdminsListResponse.builder().userIds(ids).build();
          });
    }).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SuperAdminsListResponse>> grant(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      GrantSuperAdminRequest body) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = JwtUtils.extractUserId(bearerToken(authorization));

      if (body == null || StringUtils.isBlank(body.getUserId())) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("userId is required"));
      }

      String targetUserId = body.getUserId().trim();

      return fga.isSuperAdmin(callerUserId)
          .flatMap(isSa -> {
            if (!Boolean.TRUE.equals(isSa)) {
              return Single.error(new ForbiddenOperationException("Only superadmins can grant superadmin"));
            }
            log.info("AUDIT superadmin_grant: caller={} target={} ts={}", callerUserId, targetUserId, Instant.now());
            return fga.assignSuperAdmin(targetUserId).andThen(fga.getSuperAdmins());
          })
          .map(set -> {
            List<String> ids = new ArrayList<>(set);
            ids.sort(String::compareTo);
            return SuperAdminsListResponse.builder().userIds(ids).build();
          });
    }).to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{userId}")
  public CompletionStage<Response<SuperAdminsListResponse>> revoke(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @PathParam("userId") String targetUserId) {
    return Single.defer(() -> {
      OpenFgaService fga = requireOpenFga();
      String callerUserId = JwtUtils.extractUserId(bearerToken(authorization));

      if (StringUtils.isBlank(targetUserId)) {
        return Single.error(
            ServiceError.INCORRECT_OR_MISSING_PATH_PARAMETERS.getCustomException(
                "userId path parameter is required"));
      }

      return fga.isSuperAdmin(callerUserId)
          .flatMap(isSa -> {
            if (!Boolean.TRUE.equals(isSa)) {
              return Single.error(new ForbiddenOperationException("Only superadmins can revoke superadmin"));
            }
            return fga.getSuperAdmins();
          })
          .flatMap(admins -> {
            if (!admins.contains(targetUserId)) {
              return Single.error(ServiceError.NOT_FOUND.getCustomException("User is not a superadmin"));
            }
            if (admins.size() <= 1) {
              return Single.error(
                  ServiceError.INVALID_REQUEST_PARAM.getCustomException("Cannot remove last superadmin"));
            }
            log.info("AUDIT superadmin_revoke: caller={} target={} ts={}", callerUserId, targetUserId, Instant.now());
            return fga.revokeSuperAdmin(targetUserId).andThen(fga.getSuperAdmins());
          })
          .map(set -> {
            List<String> ids = new ArrayList<>(set);
            ids.sort(String::compareTo);
            return SuperAdminsListResponse.builder().userIds(ids).build();
          });
    }).to(RestResponse.jaxrsRestHandler());
  }
}
