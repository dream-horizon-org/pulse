package org.dreamhorizon.pulseserver.resources.v1.users;

import static org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
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
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.userapikey.UserApiKeyService;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.util.CompletableFutureUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/users/me/api-keys")
public class UserApiKeyController {

  private final UserApiKeyService userApiKeyService;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<UserApiKeyPublicInfo>>> listApiKeys(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
    String userId = resolveUserId(authorization);
    return userApiKeyService.listApiKeys(userId)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<UserApiKeyInfo>> createApiKey(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @NotNull CreateUserApiKeyRequest request) {
    String userId = resolveUserId(authorization);
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
    String userId = resolveUserId(authorization);
    return userApiKeyService.revokeApiKey(keyId, userId, userId)
        .andThen(Single.fromCallable(() -> Response.successfulResponse((Void) null)))
        .to(CompletableFutureUtils::fromSingle);
  }

  private String resolveUserId(String authorization) {
    try {
      return extractUserId(authorization);
    } catch (Exception e) {
      log.debug("Invalid token in UserApiKeyController: {}", e.getMessage());
      throw ServiceError.UNAUTHORISED.getException();
    }
  }

  @Data
  public static class CreateUserApiKeyRequest {
    @NotBlank
    private String displayName;
  }
}
