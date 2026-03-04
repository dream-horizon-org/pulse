package org.dreamhorizon.pulseserver.resources.v1.auth;

import com.google.inject.Inject;
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
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginRequest;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.AuthService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/auth")
public class Authenticate {
  final AuthService authService;

  /**
   * Legacy authentication endpoint with tenant-id header.
   * Used by older clients that provide tenant context upfront during authentication.
   * 
   * <p>This endpoint is maintained for backward compatibility with existing integrations.
   * It expects the client to already know and provide the tenant ID during login.
   * 
   * @param authenticateRequestDto Request containing Firebase ID token
   * @param tenantId Tenant ID provided by client (optional in newer flows)
   * @return AuthenticateResponseDto with tokens and user information
   * @deprecated Consider migrating to /login endpoint which handles tenant lookup automatically
   */
  @POST
  @Path("/social/authenticate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AuthenticateResponseDto>> getAccessAndRefreshTokens(
      @RequestBody(description = "Request body to authenticate user")
      @Valid
      AuthenticateRequestDto authenticateRequestDto,
      @HeaderParam("tenant-id") String tenantId) {
    try {
      return authService
          .verifyGoogleIdToken(authenticateRequestDto.identifier, tenantId)
          .to(RestResponse.jaxrsRestHandler());
    } catch (Exception e) {
      String cause = e.getMessage() != null ? e.getMessage() : "Invalid ID token";
      throw ServiceError.SERVICE_UNKNOWN_EXCEPTION.getCustomException("Something went wrong", cause);
    }
  }

  /**
   * Simplified login endpoint for Firebase authentication.
   * This is the preferred authentication method for new implementations.
   * 
   * <p>This endpoint automatically:
   * <ul>
   *   <li>Determines user's tenant from OpenFGA relationships</li>
   *   <li>Detects if user needs onboarding (no tenant assigned)</li>
   *   <li>Returns complete user context including projects and roles</li>
   * </ul>
   * 
   * <p>The client should check the 'needsOnboarding' flag in the response:
   * <ul>
   *   <li>If true: Redirect to onboarding flow</li>
   *   <li>If false: User is authenticated and can access their tenant/projects</li>
   * </ul>
   * 
   * @param loginRequest Request containing Firebase ID token
   * @return LoginResponse with authentication status, tokens, and user context
   */
  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<LoginResponse>> login(
      @RequestBody(description = "Login with Firebase ID token")
      @Valid
      LoginRequest loginRequest) {
    try {
      return authService
          .login(loginRequest.getFirebaseIdToken())
          .to(RestResponse.jaxrsRestHandler());
    } catch (Exception e) {
      String cause = e.getMessage() != null ? e.getMessage() : "Invalid ID token";
      throw ServiceError.SERVICE_UNKNOWN_EXCEPTION.getCustomException("Login failed", cause);
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
  public CompletionStage<Response<GetAccessTokenFromRefreshTokenResponseDto>> getAccessTokenFromRefreshToken(
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
