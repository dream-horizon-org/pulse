package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackChannelListDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthCallbackRequest;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.oauth.SlackOAuthService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/notifications/integrations/slack")
public class SlackOAuthController {

  final SlackOAuthService slackOAuthService;

  @GET
  @Path("/install")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<String>> install(
      @HeaderParam("X-Project-Id")
      @NotBlank(message = "X-Project-Id header is required")
      String projectId) {
    return slackOAuthService.generateInstallUrl(projectId).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/callback")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SlackOAuthResponseDto>> callback(
      @BeanParam SlackOAuthCallbackRequest request) {

    if (request.hasError()) {
      log.warn("Slack OAuth denied by user: {}", request.getError());
      return Single.just(SlackOAuthResponseDto.builder()
              .success(false)
              .message("User denied Slack authorization: " + request.getError())
              .build())
          .to(RestResponse.jaxrsRestHandler());
    }

    if (!request.isValid()) {
      return Single.just(SlackOAuthResponseDto.builder()
              .success(false)
              .message(request.getValidationError())
              .build())
          .to(RestResponse.jaxrsRestHandler());
    }

    return slackOAuthService
        .exchangeCodeForToken(request.getCode())
        .flatMap(oauthResult ->
            slackOAuthService.createOrUpdateSlackChannel(request.getProjectId(), oauthResult)
                .map(channel -> SlackOAuthResponseDto.builder()
                    .success(true)
                    .workspaceId(oauthResult.getWorkspaceId())
                    .workspaceName(oauthResult.getWorkspaceName())
                    .channelId(channel.getId())
                    .message("Slack integration configured successfully")
                    .build()))
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/channels")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<SlackChannelListDto>>> listChannels(
      @HeaderParam("X-Project-Id")
      @NotBlank(message = "X-Project-Id header is required")
      String projectId) {
    return slackOAuthService.listWorkspaceChannels(projectId).to(RestResponse.jaxrsRestHandler());
  }
}
