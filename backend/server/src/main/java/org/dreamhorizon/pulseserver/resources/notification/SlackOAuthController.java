package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.config.NotificationConfig.SlackOAuthConfig;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackChannelListDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthCallbackRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.oauth.SlackOAuthService;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

@Slf4j
@Path("/v1/notifications/integrations/slack")
public class SlackOAuthController {

  private static final String STATUS_ERROR = "error";
  private static final String STATUS_SUCCESS = "success";

  private final SlackOAuthService slackOAuthService;
  private final SlackOAuthConfig config;

  @Inject
  public SlackOAuthController(Vertx vertx, SlackOAuthService slackOAuthService) {
    this.slackOAuthService = slackOAuthService;
    this.config = SharedDataUtils.get(vertx, NotificationConfig.class).getSlackOAuthConfig();
  }

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
  public CompletionStage<jakarta.ws.rs.core.Response> callback(
      @BeanParam SlackOAuthCallbackRequest request) {

    if (request.hasError()) {
      log.warn("Slack OAuth denied by user: {}", request.getError());
      return Single.just(buildRedirect(STATUS_ERROR, request.getError()))
          .to(RestResponse.toCompletion());
    }

    if (!request.isValid()) {
      return Single.just(buildRedirect(STATUS_ERROR, request.getValidationError()))
          .to(RestResponse.toCompletion());
    }

    return slackOAuthService
        .exchangeCodeForToken(request.getCode())
        .flatMap(oauthResult ->
            slackOAuthService
                .createOrUpdateSlackChannel(request.getProjectId(), oauthResult)
                .map(channel -> buildRedirect(STATUS_SUCCESS,
                    oauthResult.getWorkspaceName() != null
                        ? oauthResult.getWorkspaceName()
                        : "Slack")))
        .onErrorReturn(err -> {
          log.error("Slack OAuth callback failed", err);
          return buildRedirect(STATUS_ERROR, err.getMessage());
        })
        .to(RestResponse.toCompletion());
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

  private jakarta.ws.rs.core.Response buildRedirect(String status, String message) {
    String base = config.getUiRedirectUrl();
    String separator = base.contains("?") ? "&" : "?";
    String url = base + separator
        + "slack=" + encode(status)
        + "&message=" + encode(message);
    return jakarta.ws.rs.core.Response.temporaryRedirect(URI.create(url)).build();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
