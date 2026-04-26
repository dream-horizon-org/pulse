package org.dreamhorizon.pulseserver.resources.v1.auth;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.userapikey.UserApiKeyService;
import org.dreamhorizon.pulseserver.dao.user.UserDao;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/auth/api-key")
public class ApiKeyExchangeController {

  private final UserApiKeyService userApiKeyService;
  private final UserDao userDao;
  private final JwtService jwtService;
  private final OpenFgaService openFgaService;

  @POST
  @Path("/exchange")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ExchangeResponse>> exchange(@Valid ExchangeRequest request) {
    return userApiKeyService.validateAndGetUserId(request.getApiKey())
        .switchIfEmpty(Single.error(ServiceError.UNAUTHORISED.getCustomException(
            "Invalid or revoked API key", "API key not found or inactive")))
        .flatMap(userId -> userDao.getUserById(userId)
            .switchIfEmpty(Single.error(ServiceError.UNAUTHORISED.getCustomException(
                "User not found", "No user for this API key")))
            .flatMap(user -> openFgaService.getUserTenants(userId)
                .flatMap(tenantIds -> {
                  if (tenantIds == null || tenantIds.isEmpty()) {
                    return Single.error(ServiceError.UNAUTHORISED.getCustomException(
                        "User has no tenant", "No tenant assigned"));
                  }
                  String tenantId = tenantIds.get(0);
                  String accessToken = jwtService.generateAccessToken(
                      user.getUserId(), user.getEmail(), user.getName(), tenantId);
                  String refreshToken = jwtService.generateRefreshToken(
                      user.getUserId(), user.getEmail(), user.getName(), tenantId);
                  return Single.just(new ExchangeResponse(accessToken, refreshToken));
                })))
        .to(RestResponse.jaxrsRestHandler());
  }

  @Data
  public static class ExchangeRequest {
    @NotBlank(message = "apiKey must not be blank")
    private String apiKey;
  }

  @Data
  @lombok.AllArgsConstructor
  public static class ExchangeResponse {
    private String accessToken;
    private String refreshToken;
  }
}
