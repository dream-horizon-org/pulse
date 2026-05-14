package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebClientSlackPosterTest {

  private WebClient webClient;
  private HttpRequest<Buffer> httpRequest;
  private WebClientSlackPoster poster;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    webClient = mock(WebClient.class);
    httpRequest = mock(HttpRequest.class);

    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

    poster = new WebClientSlackPoster(webClient);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldPostToSlackApiWithBearerToken() {
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    JsonObject responseBody = new JsonObject().put("ok", true);
    when(httpResponse.bodyAsJsonObject()).thenReturn(responseBody);
    when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
        .thenReturn(Single.just(httpResponse));

    JsonObject result = poster.postMessage("C123", "xoxb-token", "hello").blockingGet();

    assertThat(result.getBoolean("ok")).isTrue();
    verify(webClient).postAbs("https://slack.com/api/chat.postMessage");
    verify(httpRequest).putHeader("Content-Type", "application/json");
    verify(httpRequest).putHeader("Authorization", "Bearer xoxb-token");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldSendChannelAndTextInPayload() {
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    JsonObject responseBody = new JsonObject().put("ok", true);
    when(httpResponse.bodyAsJsonObject()).thenReturn(responseBody);

    ArgumentCaptor<JsonObject> payloadCaptor = ArgumentCaptor.forClass(JsonObject.class);
    when(httpRequest.rxSendJsonObject(payloadCaptor.capture()))
        .thenReturn(Single.just(httpResponse));

    poster.postMessage("C_CHAN", "xoxb-t", "alert text").blockingGet();

    JsonObject payload = payloadCaptor.getValue();
    assertThat(payload.getString("channel")).isEqualTo("C_CHAN");
    assertThat(payload.getString("text")).isEqualTo("alert text");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldReturnResponseBodyWhenSlackReturnsError() {
    HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
    JsonObject responseBody = new JsonObject().put("ok", false).put("error", "channel_not_found");
    when(httpResponse.bodyAsJsonObject()).thenReturn(responseBody);
    when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
        .thenReturn(Single.just(httpResponse));

    JsonObject result = poster.postMessage("C_BAD", "xoxb-t", "msg").blockingGet();

    assertThat(result.getBoolean("ok")).isFalse();
    assertThat(result.getString("error")).isEqualTo("channel_not_found");
  }
}
