package org.dreamhorizon.pulseserver.constant;

import java.util.Set;

public final class NotificationConstants {
  // Common JSON body keys
  public static final String KEY_SUBJECT = "subject";
  public static final String KEY_TITLE = "title";
  public static final String KEY_TEXT = "text";
  public static final String KEY_HTML = "html";
  public static final String KEY_BODY = "body";
  public static final String KEY_BLOCKS = "blocks";
  public static final String KEY_TYPE = "type";
  public static final String KEY_ACTIONS = "actions";

  // Default values
  public static final String DEFAULT_SUBJECT = "Notification";
  public static final String DEFAULT_CHARSET = "UTF-8";
  public static final String DEFAULT_AWS_REGION = "us-east-1";

  // Environment variable keys
  public static final String ENV_AWS_REGION = "AWS_REGION";

  // Email/SES constants
  public static final class Email {

    public static final Set<String> PERMANENT_ERROR_CODES =
        Set.of(
            "MessageRejected",
            "MailFromDomainNotVerified",
            "ConfigurationSetDoesNotExist",
            "InvalidParameterValue",
            "AccountSendingPausedException");
  }

  // Slack constants
  public static final class Slack {
    public static final String API_URL = "https://slack.com/api/chat.postMessage";
    public static final String OAUTH_AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize";
    public static final String OAUTH_ACCESS_URL = "https://slack.com/api/oauth.v2.access";

    public static final String KEY_CHANNEL = "channel";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ICON_EMOJI = "icon_emoji";
    public static final String KEY_OK = "ok";
    public static final String KEY_ERROR = "error";
    public static final String KEY_TS = "ts";
    public static final String KEY_CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String KEY_AUTHORIZATION = "Authorization";
    public static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    public static final Set<String> PERMANENT_ERROR_CODES =
        Set.of(
            "channel_not_found",
            "invalid_auth",
            "account_inactive",
            "token_revoked",
            "no_permission",
            "org_login_required",
            "team_access_not_granted");
  }

  // Teams constants
  public static final class Teams {
    private Teams() {}

    public static final String CONTENT_TYPE_ADAPTIVE_CARD =
        "application/vnd.microsoft.card.adaptive";
    public static final String ADAPTIVE_CARD_SCHEMA =
        "http://adaptivecards.io/schemas/adaptive-card.json";
    public static final String ADAPTIVE_CARD_VERSION = "1.4";

    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_ADAPTIVE_CARD = "AdaptiveCard";
    public static final String TYPE_TEXT_BLOCK = "TextBlock";

    public static final String KEY_ATTACHMENTS = "attachments";
    public static final String KEY_CONTENT_TYPE = "contentType";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_SCHEMA = "$schema";
    public static final String KEY_VERSION = "version";
    public static final String KEY_WEIGHT = "weight";
    public static final String KEY_SIZE = "size";
    public static final String KEY_WRAP = "wrap";

    public static final String WEIGHT_BOLDER = "bolder";
    public static final String SIZE_MEDIUM = "medium";

    public static final Set<Integer> PERMANENT_ERROR_STATUS_CODES = Set.of(400, 401, 403, 404);
  }

  // HTTP timeouts (in seconds)
  public static final int HTTP_CONNECT_TIMEOUT_SECONDS = 10;
  public static final int HTTP_REQUEST_TIMEOUT_SECONDS = 30;
}
