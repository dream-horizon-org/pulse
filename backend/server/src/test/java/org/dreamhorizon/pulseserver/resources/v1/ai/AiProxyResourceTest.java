package org.dreamhorizon.pulseserver.resources.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AiProxyResourceTest {

  private static final String AI_SERVICE_URL = "http://ai-service:8000";
  private static final String VALID_TOKEN = "Bearer valid-jwt-token";

  @Mock
  JwtService jwtService;

  @Mock
  HttpClient httpClient;

  @Mock
  UriInfo uriInfo;

  AiProxyResource resource;

  @BeforeEach
  void setUp() {
    resource = new AiProxyResource(jwtService, httpClient, AI_SERVICE_URL, "");
  }

  private void setupUriInfo(String path, String queryString) {
    String uri = "http://localhost:8080/v1/ai/" + path;
    boolean hasQuery = queryString != null && !queryString.isEmpty();
    if (hasQuery) {
      uri += "?" + queryString;
    }
    when(uriInfo.getRequestUri()).thenReturn(URI.create(uri));
  }

  private void setupUriInfo(String path) {
    setupUriInfo(path, null);
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<InputStream> createMockResponse(
      int statusCode, String contentType, String body) {
    HttpResponse<InputStream> response = (HttpResponse<InputStream>) org.mockito.Mockito.mock(
        HttpResponse.class);
    HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.firstValue("Content-Type")).thenReturn(Optional.of(contentType));
    when(response.headers()).thenReturn(headers);
    when(response.statusCode()).thenReturn(statusCode);
    when(response.body()).thenReturn(
        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    return response;
  }

  private void setupSuccessfulProxy(String path, int statusCode, String contentType, String body) {
    setupUriInfo(path);
    when(jwtService.verifyToken("valid-jwt-token")).thenReturn(null);
    HttpResponse<InputStream> mockResponse = createMockResponse(statusCode, contentType, body);
    when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(mockResponse));
  }

  private Response awaitResponse(CompletionStage<Response> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  class AuthValidation {

    @Test
    void shouldThrow401WhenMissingAuthHeader() {
      assertThatThrownBy(() -> resource.proxyGet("chat", null, uriInfo))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
              .isEqualTo(401));
    }

    @Test
    void shouldThrow401WhenInvalidFormat() {
      assertThatThrownBy(() -> resource.proxyGet("chat", "Basic abc123", uriInfo))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
              .isEqualTo(401));
    }

    @Test
    void shouldThrow401WhenEmptyToken() {
      assertThatThrownBy(() -> resource.proxyGet("chat", "Bearer ", uriInfo))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
              .isEqualTo(401));
    }

    @Test
    void shouldThrow401WhenTokenIsWhitespaceOnly() {
      assertThatThrownBy(() -> resource.proxyGet("chat", "Bearer   ", uriInfo))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
              .isEqualTo(401));
    }

    @Test
    void shouldThrow401WhenInvalidOrExpiredToken() {
      when(jwtService.verifyToken("bad-token"))
          .thenThrow(new RuntimeException("Token expired"));

      assertThatThrownBy(() -> resource.proxyGet("chat", "Bearer bad-token", uriInfo))
          .isInstanceOf(WebApplicationException.class)
          .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
              .isEqualTo(401));
    }
  }

  @Nested
  class HttpMethods {

    @Test
    void shouldProxyGetRequest() {
      setupSuccessfulProxy("chat", 200, "application/json", "{\"message\":\"hello\"}");

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("application/json");

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("GET");
      assertThat(captor.getValue().uri().toString()).isEqualTo(AI_SERVICE_URL + "/chat");
    }

    @Test
    void shouldProxyPostRequestWithBody() {
      setupSuccessfulProxy("chat", 200, "application/json", "{\"reply\":\"ok\"}");

      InputStream body = new ByteArrayInputStream(
          "{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
      Response response = awaitResponse(
          resource.proxyPost("chat", VALID_TOKEN, uriInfo, body));

      assertThat(response.getStatus()).isEqualTo(200);

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("POST");
    }

    @Test
    void shouldProxyPostRequestWithoutBody() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      Response response = awaitResponse(
          resource.proxyPost("chat", VALID_TOKEN, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("POST");
    }

    @Test
    void shouldProxyPutRequestWithBody() {
      setupSuccessfulProxy("chat/123", 200, "application/json", "{\"updated\":true}");

      InputStream body = new ByteArrayInputStream(
          "{\"data\":\"value\"}".getBytes(StandardCharsets.UTF_8));
      Response response = awaitResponse(
          resource.proxyPut("chat/123", VALID_TOKEN, uriInfo, body));

      assertThat(response.getStatus()).isEqualTo(200);

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("PUT");
    }

    @Test
    void shouldProxyPutRequestWithoutBody() {
      setupSuccessfulProxy("chat/123", 200, "application/json", "{}");

      Response response = awaitResponse(
          resource.proxyPut("chat/123", VALID_TOKEN, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("PUT");
    }

    @Test
    void shouldProxyDeleteRequest() {
      setupSuccessfulProxy("chat/123", 204, "application/json", "");

      Response response = awaitResponse(
          resource.proxyDelete("chat/123", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(204);

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().method()).isEqualTo("DELETE");
    }
  }

  @Nested
  class RequestBuilding {

    @Test
    void shouldConstructUrlWithPath() {
      setupSuccessfulProxy("chat/messages", 200, "application/json", "{}");

      awaitResponse(resource.proxyGet("chat/messages", VALID_TOKEN, uriInfo));

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().uri().toString())
          .isEqualTo(AI_SERVICE_URL + "/chat/messages");
    }

    @Test
    void shouldIncludeQueryStringInUrl() {
      setupUriInfo("chat", "limit=10&offset=0");
      when(jwtService.verifyToken("valid-jwt-token")).thenReturn(null);
      HttpResponse<InputStream> mockResponse = createMockResponse(
          200, "application/json", "{}");
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().uri().toString())
          .contains("limit=10")
          .contains("offset=0");
    }

    @Test
    void shouldIncludeServiceKeyHeaderWhenConfigured() {
      resource = new AiProxyResource(jwtService, httpClient, AI_SERVICE_URL, "secret-service-key");
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().headers().firstValue("X-Pulse-Service-Key"))
          .contains("secret-service-key");
    }

    @Test
    void shouldNotIncludeServiceKeyHeaderWhenEmpty() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().headers().firstValue("X-Pulse-Service-Key")).isEmpty();
    }
  }

  @Nested
  class ResponseHandling {

    @Test
    void shouldReturnBufferedJsonResponse() {
      String jsonBody = "{\"message\":\"hello world\"}";
      setupSuccessfulProxy("chat", 200, "application/json", jsonBody);

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("application/json");
      assertThat(response.getEntity()).isInstanceOf(String.class);
      assertThat(response.getEntity()).isEqualTo(jsonBody);
    }

    @Test
    void shouldReturnStreamingResponseForSseContentType() {
      setupSuccessfulProxy("chat", 200, "text/event-stream", "data: {\"chunk\":1}\n\n");

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("text/event-stream");
      assertThat(response.getEntity()).isInstanceOf(StreamingOutput.class);
    }

    @Test
    void shouldPropagateStatusCodeFromUpstream() {
      setupSuccessfulProxy("chat", 404, "application/json", "{\"error\":\"not found\"}");

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class ErrorHandling {

    @Test
    void shouldReturn502WhenHttpClientThrows() {
      setupUriInfo("chat");
      when(jwtService.verifyToken("valid-jwt-token")).thenReturn(null);
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(CompletableFuture.failedFuture(
              new RuntimeException("Connection refused")));

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(502);
      assertThat(response.getEntity().toString()).contains("AI service unavailable");
    }

    @Test
    void shouldReturn502WhenHttpClientTimesOut() {
      setupUriInfo("chat");
      when(jwtService.verifyToken("valid-jwt-token")).thenReturn(null);
      CompletableFuture<HttpResponse<InputStream>> future = new CompletableFuture<>();
      future.completeExceptionally(new java.util.concurrent.TimeoutException("Timeout"));
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(future);

      Response response = awaitResponse(resource.proxyGet("chat", VALID_TOKEN, uriInfo));

      assertThat(response.getStatus()).isEqualTo(502);
    }
  }

  @Nested
  class EdgeCases {

    @Test
    void shouldHandleNullBodyInPost() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      Response response = awaitResponse(
          resource.proxyPost("chat", VALID_TOKEN, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldHandleEmptyBodyInPost() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      InputStream emptyBody = new ByteArrayInputStream(new byte[0]);
      Response response = awaitResponse(
          resource.proxyPost("chat", VALID_TOKEN, uriInfo, emptyBody));

      assertThat(response.getStatus()).isEqualTo(200);
    }
  }
}
