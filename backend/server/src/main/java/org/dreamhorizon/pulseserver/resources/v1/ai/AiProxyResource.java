package org.dreamhorizon.pulseserver.resources.v1.ai;

import com.dream11.rest.annotation.Timeout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

/**
 * Authenticates requests via JWT and proxies them to the Pulse AI agent service.
 * Supports SSE streaming for real-time responses.
 */
@Slf4j
@Path("/v1/ai")
@Timeout(value = 120000, httpStatusCode = 504)
public class AiProxyResource {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String SERVICE_KEY_HEADER = "X-Pulse-Service-Key";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final String RCA_REPORT_PATH = "rca/report";
  private static final String INTERACTION_NAME_FIELD = "interactionName";
  private static final String DATE_FIELD = "date";
  private static final String CACHE_SEPARATOR = "::";
  private static final long RCA_REPORT_CACHE_TTL_SECONDS = 24 * 60 * 60;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final int STREAM_BUFFER_SIZE = 1024;
  private static final int HTTP_BAD_GATEWAY = 502;
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String ERROR_INTERNAL_RCA = "{\"error\":\"Internal error generating RCA report\"}";

  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";

  private final JwtService jwtService;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String aiServiceUrl;
  private final String serviceKey;
  private final RootCauseService rootCauseService;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final ConcurrentHashMap<String, CachedRcaReport> rcaReportCache = new ConcurrentHashMap<>();

  @Inject
  public AiProxyResource(JwtService jwtService, ObjectMapper objectMapper,
      ApplicationConfig config, RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this(jwtService, objectMapper,
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build(),
        config.getAiServiceUrl(),
        config.getAiServiceKey(),
        rootCauseService,
        rcaReportCacheDao);
  }

  AiProxyResource(JwtService jwtService, ObjectMapper objectMapper, HttpClient httpClient,
      String aiServiceUrl,
      String serviceKey,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this.jwtService = jwtService;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.aiServiceUrl = aiServiceUrl != null && !aiServiceUrl.isBlank()
        ? aiServiceUrl : DEFAULT_AI_SERVICE_URL;
    this.serviceKey = serviceKey != null ? serviceKey : "";
    this.rootCauseService = rootCauseService;
    this.rcaReportCacheDao = rcaReportCacheDao;
    log.info("AI proxy initialized → {}", this.aiServiceUrl);
  }

  @GET
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyGet(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam(PROJECT_HEADER) String projectId,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return executeProxy(buildRequest("GET", path, null, authorization, projectId, uriInfo));
  }

  @POST
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPost(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam(PROJECT_HEADER) String projectId,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    boolean isRcaReportPath = RCA_REPORT_PATH.equals(path);
    if (!isRcaReportPath) {
      return executeProxy(buildRequest("POST", path, body, authorization, projectId, uriInfo));
    }
    return proxyRcaReportPost(path, body, authorization, projectId, uriInfo);
  }

  @PUT
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPut(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam(PROJECT_HEADER) String projectId,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    return executeProxy(buildRequest("PUT", path, body, authorization, projectId, uriInfo));
  }

  @DELETE
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyDelete(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam(PROJECT_HEADER) String projectId,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return executeProxy(buildRequest("DELETE", path, null, authorization, projectId, uriInfo));
  }

  private CompletionStage<Response> proxyRcaReportPost(
      String path,
      String body,
      String authorization,
      String projectId,
      UriInfo uriInfo
  ) {
    String targetUrl = buildTargetUrl(path, uriInfo);
    Optional<RcaCacheKeyParts> keyPartsOpt = resolveRcaReportCacheKeyParts(body, projectId);
    String cacheKey = keyPartsOpt.map(RcaCacheKeyParts::toCacheKey).orElse(null);

    if (cacheKey != null) {
      CachedRcaReport cached = rcaReportCache.get(cacheKey);
      boolean isCachePresent = cached != null;
      if (isCachePresent && !cached.isExpired()) {
        return withRcaErrorLogging(java.util.concurrent.CompletableFuture.completedFuture(
            Response.status(cached.statusCode)
                .type(cached.contentType)
                .entity(applyCachedFlag(cached.body, true))
                .build()));
      }
      if (isCachePresent) {
        rcaReportCache.remove(cacheKey);
      }
    }

    if (cacheKey != null && keyPartsOpt.isPresent()) {
      RcaCacheKeyParts keyParts = keyPartsOpt.get();
      try {
        CompletionStage<Optional<String>> mysqlStage = maybeToCompletionStage(
            rcaReportCacheDao.get(keyParts.projectId(), keyParts.interactionName(), keyParts.date()));
        return withRcaErrorLogging(mysqlStage.thenCompose(maybeBody -> {
          boolean hasMysqlHit = maybeBody.isPresent() && !maybeBody.get().isBlank();
          if (hasMysqlHit) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                Response.status(200)
                    .type(CONTENT_TYPE_JSON)
                    .entity(applyCachedFlag(maybeBody.get(), true))
                    .build());
          }
          return doEnrichAndProxyRca(targetUrl, body, authorization, projectId, cacheKey)
              .thenApply(response -> {
                storeRcaReportInMysqlIfSuccess(response, keyParts);
                return response;
              });
        }));
      } catch (Throwable t) {
        log.warn("RCA MySQL cache lookup failed, falling back to AI: {}", t.getMessage());
        return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, body, authorization, projectId, cacheKey));
      }
    }

    return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, body, authorization, projectId, cacheKey));
  }

  private CompletionStage<Response> withRcaErrorLogging(CompletionStage<Response> stage) {
    return stage.exceptionally(ex -> {
      log.error("RCA report proxy failed", ex);
      return Response.status(HTTP_INTERNAL_ERROR)
          .entity(ERROR_INTERNAL_RCA)
          .type(CONTENT_TYPE_JSON)
          .build();
    });
  }

  private CompletionStage<Response> doEnrichAndProxyRca(
      String targetUrl,
      String body,
      String authorization,
      String projectId,
      String cacheKey
  ) {
    log.info("Proxying RCA report to {}", targetUrl);
    return enrichRcaBodyAsync(body, projectId)
        .thenCompose(enrichedBody -> {
          HttpRequest request = buildRequestWithUrl("POST", targetUrl, enrichedBody, authorization, projectId);
          return executeProxy(request);
        })
        .thenApply(response -> {
          maybeStoreRcaReportCache(response, cacheKey);
          return response;
        });
  }

  private void storeRcaReportInMysqlIfSuccess(Response response, RcaCacheKeyParts keyParts) {
    boolean isSuccess = response != null && response.getStatus() >= 200 && response.getStatus() < 300;
    if (!isSuccess) {
      return;
    }
    Object entity = response.getEntity();
    boolean isStringBody = entity instanceof String;
    if (!isStringBody) {
      return;
    }
    rcaReportCacheDao.put(keyParts.projectId(), keyParts.interactionName(), keyParts.date(), (String) entity)
        .subscribe();
  }

  private static <T> java.util.concurrent.CompletionStage<Optional<T>> maybeToCompletionStage(
      io.reactivex.rxjava3.core.Maybe<T> maybe) {
    java.util.concurrent.CompletableFuture<Optional<T>> future = new java.util.concurrent.CompletableFuture<>();
    maybe.subscribe(
        value -> future.complete(Optional.of(value)),
        err -> {
          future.complete(Optional.empty());
        },
        () -> future.complete(Optional.empty())
    );
    return future;
  }

  private record RcaCacheKeyParts(String projectId, String interactionName, LocalDate date) {
    String toCacheKey() {
      return String.join(CACHE_SEPARATOR, projectId, interactionName, date.toString());
    }
  }

  private Optional<RcaCacheKeyParts> resolveRcaReportCacheKeyParts(String body, String projectId) {
    boolean isProjectIdMissing = projectId == null || projectId.isBlank();
    boolean isBodyMissing = body == null || body.isBlank();
    if (isProjectIdMissing || isBodyMissing) {
      return Optional.empty();
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode interactionNode = root.get(INTERACTION_NAME_FIELD);
      boolean isInteractionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (isInteractionMissing) {
        return Optional.empty();
      }
      String interactionName = interactionNode.asText();
      LocalDate date = resolveDateFromNode(root.get(DATE_FIELD));
      return Optional.of(new RcaCacheKeyParts(projectId, interactionName, date));
    } catch (Exception e) {
      log.debug("Unable to parse RCA cache key parts from body: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Fetches root-cause data via RootCauseService and embeds it into the request
   * body as {@code rootCausePayload}. Non-blocking: converts RxJava Single to
   * CompletableFuture so it never blocks the Vert.x event loop.
   */
  private CompletionStage<String> enrichRcaBodyAsync(String body, String projectId) {
    boolean isBodyMissing = body == null || body.isBlank();
    boolean isProjectMissing = projectId == null || projectId.isBlank();
    if (isBodyMissing || isProjectMissing) {
      return java.util.concurrent.CompletableFuture.completedFuture(body);
    }

    try {
      ObjectNode root = (ObjectNode) objectMapper.readTree(body);
      JsonNode interactionNode = root.get(INTERACTION_NAME_FIELD);
      boolean isInteractionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (isInteractionMissing) {
        return java.util.concurrent.CompletableFuture.completedFuture(body);
      }

      String interactionName = interactionNode.asText();
      LocalDate date = resolveDateFromNode(root.get(DATE_FIELD));

      java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
      rootCauseService.getRootCause(projectId, interactionName, date)
          .subscribe(
              result -> {
                try {
                  JsonNode resultNode = objectMapper.valueToTree(result);
                  root.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);
                  future.complete(objectMapper.writeValueAsString(root));
                } catch (Exception e) {
                  log.warn("Failed to serialize enriched RCA body: {}", e.getMessage());
                  future.complete(body);
                }
              },
              error -> {
                log.warn("Failed to fetch root-cause data for enrichment: {}", error.getMessage());
                future.complete(body);
              }
          );
      return future;
    } catch (Exception e) {
      log.warn("Failed to parse RCA body for enrichment: {}", e.getMessage());
      return java.util.concurrent.CompletableFuture.completedFuture(body);
    }
  }

  private LocalDate resolveDateFromNode(JsonNode dateNode) {
    if (dateNode == null || dateNode.isNull()) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    String dateValue = dateNode.asText();
    boolean isDateMissing = dateValue == null || dateValue.isBlank();
    if (isDateMissing) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    try {
      return LocalDate.parse(dateValue);
    } catch (DateTimeParseException e) {
      return LocalDate.now(ZoneOffset.UTC);
    }
  }

  private void validateAuth(String authorization) {
    boolean isMissingBearer = authorization == null || !authorization.startsWith(BEARER_PREFIX);
    if (isMissingBearer) {
      throw new WebApplicationException("Missing or invalid Authorization header", 401);
    }

    String token = authorization.substring(BEARER_PREFIX.length()).trim();
    boolean isTokenEmpty = token.isEmpty();
    if (isTokenEmpty) {
      throw new WebApplicationException("Empty authorization token", 401);
    }

    try {
      jwtService.verifyToken(token);
    } catch (Exception e) {
      log.debug("JWT verification failed: {}", e.getMessage());
      throw new WebApplicationException("Invalid or expired token", 401);
    }
  }

  private HttpRequest buildRequest(String method, String path, String body,
      String authorization, String projectId, UriInfo uriInfo) {
    String targetUrl = buildTargetUrl(path, uriInfo);
    return buildRequestWithUrl(method, targetUrl, body, authorization, projectId);
  }

  /**
   * Builds an HttpRequest using a pre-resolved URL. Use this variant when the
   * request is built on a background thread where JAX-RS UriInfo is unavailable.
   */
  private HttpRequest buildRequestWithUrl(String method, String targetUrl, String body,
      String authorization, String projectId) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .header(AUTHORIZATION_HEADER, authorization);

    if (projectId != null && !projectId.isBlank()) {
      builder.header(PROJECT_HEADER, projectId.trim());
    }

    boolean hasServiceKey = !serviceKey.isEmpty();
    if (hasServiceKey) {
      builder.header(SERVICE_KEY_HEADER, serviceKey);
    }

    applyMethodAndBody(builder, method, body);
    return builder.build();
  }

  private String buildTargetUrl(String path, UriInfo uriInfo) {
    String queryString = uriInfo.getRequestUri().getRawQuery();
    boolean hasQuery = queryString != null && !queryString.isEmpty();
    return hasQuery
        ? aiServiceUrl + "/" + path + "?" + queryString
        : aiServiceUrl + "/" + path;
  }

  private void applyMethodAndBody(HttpRequest.Builder builder, String method, String body) {
    boolean hasBody = body != null && !body.isEmpty();
    switch (method) {
      case "POST":
      case "PUT":
        if (hasBody) {
          builder.header("Content-Type", CONTENT_TYPE_JSON);
        }
        HttpRequest.BodyPublisher publisher = hasBody
            ? BodyPublishers.ofString(body)
            : BodyPublishers.noBody();
        if ("POST".equals(method)) {
          builder.POST(publisher);
        } else {
          builder.PUT(publisher);
        }
        break;
      case "DELETE":
        builder.DELETE();
        break;
      default:
        builder.GET();
        break;
    }
  }

  private CompletionStage<Response> executeProxy(HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(this::buildResponse)
        .exceptionally(ex -> {
          log.error("AI proxy error for {}: {}", request.uri(), ex.getMessage());
          return badGatewayResponse();
        });
  }

  private Response buildResponse(HttpResponse<InputStream> response) {
    String contentType = response.headers()
        .firstValue("Content-Type")
        .orElse(CONTENT_TYPE_JSON);

    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    if (isSse) {
      return buildStreamingResponse(response, contentType);
    }
    return buildBufferedResponse(response, contentType);
  }

  private Response buildStreamingResponse(HttpResponse<InputStream> response, String contentType) {
    InputStream body = response.body();
    StreamingOutput stream = output -> {
      try (InputStream is = body) {
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
          output.write(buf, 0, bytesRead);
          output.flush();
        }
      }
    };

    return Response.status(response.statusCode())
        .entity(stream)
        .type(contentType)
        .build();
  }

  private Response buildBufferedResponse(HttpResponse<InputStream> response, String contentType) {
    try (InputStream is = response.body()) {
      String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return Response.status(response.statusCode())
          .entity(responseBody)
          .type(contentType)
          .build();
    } catch (IOException e) {
      log.error("Failed to read AI service response: {}", e.getMessage());
      return badGatewayResponse();
    }
  }

  private Response badGatewayResponse() {
    return Response.status(HTTP_BAD_GATEWAY)
        .entity("{\"error\":\"AI service unavailable\"}")
        .type(CONTENT_TYPE_JSON)
        .build();
  }

  private String readBodySafe(InputStream bodyStream) {
    if (bodyStream == null) {
      return null;
    }
    try {
      byte[] bytes = bodyStream.readAllBytes();
      boolean isEmpty = bytes.length == 0;
      return isEmpty ? null : new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.debug("Failed to read request body: {}", e.getMessage());
      return null;
    }
  }

  private void maybeStoreRcaReportCache(Response response, String cacheKey) {
    boolean shouldSkip = cacheKey == null || response == null;
    if (shouldSkip) {
      return;
    }
    boolean isSuccess = response.getStatus() >= 200 && response.getStatus() < 300;
    boolean isJson = response.getMediaType() != null
        && response.getMediaType().toString().contains(CONTENT_TYPE_JSON);
    Object entity = response.getEntity();
    if (!isSuccess || !isJson || !(entity instanceof String)) {
      return;
    }

    String body = (String) entity;
    CachedRcaReport cacheValue = new CachedRcaReport(
        response.getStatus(),
        CONTENT_TYPE_JSON,
        body,
        Instant.now()
    );
    rcaReportCache.put(cacheKey, cacheValue);
  }

  private String applyCachedFlag(String body, boolean cached) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode) {
        ((ObjectNode) node).put("cached", cached);
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception exception) {
      return body;
    }
  }

  private static final class CachedRcaReport {
    private final int statusCode;
    private final String contentType;
    private final String body;
    private final Instant cachedAt;

    private CachedRcaReport(int statusCode, String contentType, String body, Instant cachedAt) {
      this.statusCode = statusCode;
      this.contentType = contentType;
      this.body = body;
      this.cachedAt = cachedAt;
    }

    private boolean isExpired() {
      Duration age = Duration.between(cachedAt, Instant.now());
      return age.getSeconds() > RCA_REPORT_CACHE_TTL_SECONDS;
    }
  }
}
