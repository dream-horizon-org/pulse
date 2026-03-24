package org.dreamhorizon.pulseserver.resources.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.service.ai.impl.AiProxyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiProxyControllerTest {

  private static final String AI_SERVICE_URL = "http://ai-service:8000";
  private static final String VALID_TOKEN = "Bearer valid-jwt-token";

  @Mock WebClient webClient;

  @Mock HttpRequest<Buffer> httpRequest;

  @Mock UriInfo uriInfo;

  AiProxyController controller;

  @BeforeEach
  void setUp() {
    lenient().when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.putAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
    controller = new AiProxyController(new AiProxyServiceImpl(webClient, AI_SERVICE_URL));
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

  private HttpResponse<Buffer> createMockResponse(
      int statusCode, String contentType, String body) {
    @SuppressWarnings("unchecked")
    HttpResponse<Buffer> response = org.mockito.Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(statusCode);
    when(response.getHeader("Content-Type")).thenReturn(contentType);
    when(response.body()).thenReturn(Buffer.buffer(body == null ? "" : body));
    return response;
  }

  private void setupSuccessfulProxy(String path, int statusCode, String contentType, String body) {
    setupUriInfo(path);
    HttpResponse<Buffer> mockResponse = createMockResponse(statusCode, contentType, body);
    when(httpRequest.rxSend()).thenReturn(Single.just(mockResponse));
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(mockResponse));
  }

  private Response awaitResponse(CompletionStage<Response> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  class HttpMethods {

    @Test
    void shouldProxyGetRequest() {
      setupSuccessfulProxy("chat", 200, "application/json", "{\"message\":\"hello\"}");

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("application/json");

      verify(webClient).getAbs(AI_SERVICE_URL + "/chat");
      verify(httpRequest).timeout(AiProxyServiceImpl.AI_PROXY_UPSTREAM_TIMEOUT_MS);
      verify(httpRequest).rxSend();
    }

    @Test
    void shouldProxyPostRequestWithBody() {
      setupSuccessfulProxy("chat", 200, "application/json", "{\"reply\":\"ok\"}");

      InputStream body =
          new java.io.ByteArrayInputStream(
              "{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
      Response response =
          awaitResponse(controller.proxyPost("chat", VALID_TOKEN, null, uriInfo, body));

      assertThat(response.getStatus()).isEqualTo(200);

      verify(webClient).postAbs(AI_SERVICE_URL + "/chat");
      verify(httpRequest).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldProxyPostRequestWithoutBody() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      Response response =
          awaitResponse(controller.proxyPost("chat", VALID_TOKEN, null, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);

      verify(webClient).postAbs(AI_SERVICE_URL + "/chat");
      verify(httpRequest).rxSend();
    }

    @Test
    void shouldProxyPutRequestWithBody() {
      setupSuccessfulProxy("chat/123", 200, "application/json", "{\"updated\":true}");

      InputStream body =
          new java.io.ByteArrayInputStream(
              "{\"data\":\"value\"}".getBytes(StandardCharsets.UTF_8));
      Response response =
          awaitResponse(controller.proxyPut("chat/123", VALID_TOKEN, null, uriInfo, body));

      assertThat(response.getStatus()).isEqualTo(200);

      verify(webClient).putAbs(AI_SERVICE_URL + "/chat/123");
      verify(httpRequest).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldProxyPutRequestWithoutBody() {
      setupSuccessfulProxy("chat/123", 200, "application/json", "{}");

      Response response =
          awaitResponse(controller.proxyPut("chat/123", VALID_TOKEN, null, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);

      verify(webClient).putAbs(AI_SERVICE_URL + "/chat/123");
      verify(httpRequest).rxSend();
    }

    @Test
    void shouldProxyDeleteRequest() {
      setupSuccessfulProxy("chat/123", 204, "application/json", "");

      Response response =
          awaitResponse(controller.proxyDelete("chat/123", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(204);

      verify(webClient).deleteAbs(AI_SERVICE_URL + "/chat/123");
      verify(httpRequest).rxSend();
    }
  }

  @Nested
  class RequestBuilding {

    @Test
    void shouldConstructUrlWithPath() {
      setupSuccessfulProxy("chat/messages", 200, "application/json", "{}");

      awaitResponse(controller.proxyGet("chat/messages", VALID_TOKEN, null, uriInfo));

      verify(webClient).getAbs(AI_SERVICE_URL + "/chat/messages");
    }

    @Test
    void shouldIncludeQueryStringInUrl() {
      setupUriInfo("chat", "limit=10&offset=0");
      HttpResponse<Buffer> mockResponse = createMockResponse(200, "application/json", "{}");
      when(httpRequest.rxSend()).thenReturn(Single.just(mockResponse));

      awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      verify(webClient)
          .getAbs(
              argThat(
                  (String url) -> url.contains("limit=10") && url.contains("offset=0")));
    }

    @Test
    void shouldForwardProjectIdHeaderWhenProvided() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      awaitResponse(controller.proxyGet("chat", VALID_TOKEN, "proj-123", uriInfo));

      verify(httpRequest).putHeader(eq("X-Project-ID"), eq("proj-123"));
    }

    @Test
    void shouldNotIncludeProjectIdHeaderWhenNull() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      verify(httpRequest, never()).putHeader(eq("X-Project-ID"), anyString());
    }
  }

  @Nested
  class ResponseHandling {

    @Test
    void shouldReturnBufferedJsonResponse() {
      String jsonBody = "{\"message\":\"hello world\"}";
      setupSuccessfulProxy("chat", 200, "application/json", jsonBody);

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("application/json");
      assertThat(response.getEntity()).isInstanceOf(String.class);
      assertThat(response.getEntity()).isEqualTo(jsonBody);
    }

    @Test
    void shouldReturnStreamingResponseForSseContentType() {
      setupSuccessfulProxy("chat", 200, "text/event-stream", "data: {\"chunk\":1}\n\n");

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getMediaType().toString()).contains("text/event-stream");
      assertThat(response.getEntity()).isInstanceOf(StreamingOutput.class);
    }

    @Test
    void shouldPropagateStatusCodeFromUpstream() {
      setupSuccessfulProxy("chat", 404, "application/json", "{\"error\":\"not found\"}");

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(404);
    }
  }

  @Nested
  class ErrorHandling {

    @Test
    void shouldReturn502WhenHttpClientThrows() {
      setupUriInfo("chat");
      when(httpRequest.rxSend()).thenReturn(Single.error(new RuntimeException("Connection refused")));

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(502);
      assertThat(response.getEntity().toString()).contains("AI service unavailable");
    }

    @Test
    void shouldReturn502WhenHttpClientTimesOut() {
      setupUriInfo("chat");
      when(httpRequest.rxSend())
          .thenReturn(Single.error(new java.util.concurrent.TimeoutException("Timeout")));

      Response response = awaitResponse(controller.proxyGet("chat", VALID_TOKEN, null, uriInfo));

      assertThat(response.getStatus()).isEqualTo(502);
    }
  }

  @Nested
  class EdgeCases {

    @Test
    void shouldHandleNullBodyInPost() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      Response response =
          awaitResponse(controller.proxyPost("chat", VALID_TOKEN, null, uriInfo, null));

      assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldHandleEmptyBodyInPost() {
      setupSuccessfulProxy("chat", 200, "application/json", "{}");

      InputStream emptyBody = new java.io.ByteArrayInputStream(new byte[0]);
      Response response =
          awaitResponse(controller.proxyPost("chat", VALID_TOKEN, null, uriInfo, emptyBody));

      assertThat(response.getStatus()).isEqualTo(200);
    }
  }
}
