package in.horizonos.pulseserver.resources.v1.auth;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import in.horizonos.pulseserver.error.ServiceError;
import in.horizonos.pulseserver.resources.v1.auth.models.AuthenticateRequestDto;
import in.horizonos.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import in.horizonos.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import in.horizonos.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.AuthService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/auth")
public class Authenticate {
  final AuthService authService;

  @POST
  @Path("/social/authenticate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AuthenticateResponseDto>> getAccessAndRefreshTokens(
      @RequestBody(description = "Request body to authenticate user")
      @Valid
      AuthenticateRequestDto authenticateRequestDto) {
    try {
      return authService
          .verifyGoogleIdToken(authenticateRequestDto.identifier)
          .to(RestResponse.jaxrsRestHandler());
    } catch (Exception e) {
      throw ServiceError.SERVICE_UNKNOWN_EXCEPTION.getException();
    }
  }

  @GET
  @Path("/token/verify")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<VerifyAuthTokenResponseDto>> verifyAuthToken(
      @NotNull @HeaderParam("authorization") String authorization) {
    try {
      return authService.verifyAuthToken(authorization).to(RestResponse.jaxrsRestHandler());
    } catch (Exception e) {
      throw ServiceError.SERVICE_UNKNOWN_EXCEPTION.getException();
    }
  }

  @POST
  @Path("/token/refresh")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GetAccessTokenFromRefreshTokenResponseDto>>
  getAccessTokenFromRefreshToken(
      @RequestBody(
          description =
              "Request body to get access token using refresh token from guardian service")
      @Valid
      GetAccessTokenFromRefreshTokenRequestDto getAccessTokenFromRefreshTokenRequestDto) {
    return authService
        .getAccessTokenFromRefreshToken(getAccessTokenFromRefreshTokenRequestDto)
        .to(RestResponse.jaxrsRestHandler());
  }
}
