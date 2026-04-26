package org.dreamhorizon.pulseserver.resources.v1.users;

import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.userapikey.UserApiKeyService;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/users/me/api-keys")
public class UserApiKeyController {

  private final UserApiKeyService userApiKeyService;
  private final JwtService jwtService;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<UserApiKeyPublicInfo>>> listApiKeys(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
    String userId = extractUserId(authorization);
    return userApiKeyService.listApiKeys(userId)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<UserApiKeyInfo>> createApiKey(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotNull CreateUserApiKeyRequest request) {
    String userId = extractUserId(authorization);
    return userApiKeyService.createApiKey(userId, request.getDisplayName())
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{keyId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Void>> revokeApiKey(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotNull @PathParam("keyId") Long keyId) {
    String userId = extractUserId(authorization);
    return userApiKeyService.revokeApiKey(keyId, userId, userId)
        .toSingleDefault((Void) null)
        .to(RestResponse.jaxrsRestHandler());
  }

  private String extractUserId(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw ServiceError.UNAUTHORISED.getException();
    }
    try {
      Claims claims = jwtService.verifyToken(authorization.substring(7).trim());
      return claims.getSubject();
    } catch (Exception e) {
      log.debug("Invalid token in UserApiKeyController: {}", e.getMessage());
      throw ServiceError.UNAUTHORISED.getException();
    }
  }

  public static class CreateUserApiKeyRequest {
    @NotBlank
    private String displayName;

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }
  }
}
