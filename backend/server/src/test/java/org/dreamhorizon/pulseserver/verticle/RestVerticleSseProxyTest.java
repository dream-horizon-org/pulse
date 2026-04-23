package org.dreamhorizon.pulseserver.verticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.alert.core.AlertEvaluationService;
import org.dreamhorizon.pulseserver.vertx.AiStreamingHttpClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;
import org.junit.jupiter.api.AfterEach;
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
class RestVerticleSseProxyTest {

  private static final String JWT_SECRET =
      "this-is-a-very-long-secret-key-for-jwt-signing-purposes";

  @Mock private GuiceInjector guiceInjector;
  @Mock private OpenFgaService openFgaService;
  @Mock private AlertEvaluationService alertEvaluationService;

  private Vertx coreVertx;
  private io.vertx.rxjava3.core.Vertx rxVertx;
  private io.vertx.core.http.HttpServer fakeAiServer;
  private io.vertx.rxjava3.core.http.HttpServer pulseHttpServer;

  @BeforeEach
  void setUp() throws Exception {
    RxJavaPlugins.setIoSchedulerHandler(schedule -> Schedulers.trampoline());
    coreVertx = Vertx.vertx();
    rxVertx = io.vertx.rxjava3.core.Vertx.newInstance(coreVertx);
    doNothing().when(alertEvaluationService).registerConsumers();
    setStaticGuiceInjector(guiceInjector);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (pulseHttpServer != null) {
      CompletableFuture<Void> c = new CompletableFuture<>();
      pulseHttpServer.rxClose().subscribe(() -> c.complete(null), c::completeExceptionally);
      c.get(5, TimeUnit.SECONDS);
      pulseHttpServer = null;
    }
    if (fakeAiServer != null) {
      CompletableFuture<Void> c = new CompletableFuture<>();
      fakeAiServer.close(ar -> c.complete(null));
      c.get(5, TimeUnit.SECONDS);
      fakeAiServer = null;
    }
    if (coreVertx != null) {
      CompletableFuture<Void> c = new CompletableFuture<>();
      coreVertx.close(ar -> c.complete(null));
      c.get(5, TimeUnit.SECONDS);
      coreVertx = null;
    }
    setStaticGuiceInjector(null);
    RxJavaPlugins.reset();
  }

  /** Sets {@code GuiceInjector.guiceInjector} static field directly, bypassing initialization. */
  private static void setStaticGuiceInjector(GuiceInjector value) throws Exception {
    Field field = GuiceInjector.class.getDeclaredField("guiceInjector");
    field.setAccessible(true);
    field.set(null, value);
  }

  private JwtService jwtService() {
    ApplicationConfig cfg = new ApplicationConfig();
    cfg.setJwtSecret(JWT_SECRET);
    return new JwtService(cfg);
  }

  private int startFakeAi(Consumer<io.vertx.core.http.HttpServerResponse> responder) throws Exception {
    CompletableFuture<Integer> ready = new CompletableFuture<>();
    fakeAiServer =
        coreVertx
            .createHttpServer()
            .requestHandler(
                req -> {
                  if (HttpMethod.POST.equals(req.method()) && "/run_sse".equals(req.path())) {
                    responder.accept(req.response());
                  } else {
                    req.response().setStatusCode(404).end();
                  }
                });
    fakeAiServer.listen(
        0,
        "127.0.0.1",
        ar -> {
          if (ar.succeeded()) {
            ready.complete(ar.result().actualPort());
          } else {
            ready.completeExceptionally(ar.cause());
          }
        });
    return ready.get(5, TimeUnit.SECONDS);
  }

  private int startPulse(Router router) throws Exception {
    CompletableFuture<Integer> ready = new CompletableFuture<>();
    pulseHttpServer = rxVertx.createHttpServer().requestHandler(router);
    pulseHttpServer.rxListen(0, "127.0.0.1").subscribe(
        srv -> ready.complete(srv.actualPort()),
        ready::completeExceptionally);
    return ready.get(5, TimeUnit.SECONDS);
  }

  private void bindGuice(JwtService jwtService) {
    when(guiceInjector.getInstance(AlertEvaluationService.class)).thenReturn(alertEvaluationService);
    when(guiceInjector.getInstance(JwtService.class)).thenReturn(jwtService);
    when(guiceInjector.getInstance(OpenFgaService.class)).thenReturn(openFgaService);
  }

  private Router routerFromVerticle(RestVerticle rv) {
    return ((ExposeRouter) rv).routerForTest();
  }

  /** Exposes {@link RestVerticle#getRouter()} and assigns {@code vertx} without deploying. */
  private static final class ExposeRouter extends RestVerticle {
    ExposeRouter(io.vertx.rxjava3.core.Vertx rx) {
      super(new io.vertx.core.http.HttpServerOptions().setPort(0));
      this.vertx = rx;
    }

    Router routerForTest() {
      return getRouter();
    }
  }

  @Nested
  class AuthAndPermission {

    @Test
    void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
      int aiPort = startFakeAi(resp -> resp.setStatusCode(200).end());
      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var resp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("X-Project-ID", "p1")
              .rxSendBuffer(Buffer.buffer("{}"))
              .timeout(10, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(resp.statusCode()).isEqualTo(401);
      assertThat(
              new JsonObject(resp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("message"))
          .isEqualTo(ServiceError.UNAUTHORISED.getErrorMessage());
    }

    @Test
    void shouldReturn401WhenJwtIsInvalid() throws Exception {
      int aiPort = startFakeAi(resp -> resp.setStatusCode(200).end());
      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var resp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("Authorization", "Bearer not-a-valid-jwt")
              .putHeader("X-Project-ID", "p1")
              .rxSendBuffer(Buffer.buffer("{}"))
              .timeout(10, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(resp.statusCode()).isEqualTo(401);
      assertThat(
              new JsonObject(resp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("message"))
          .isEqualTo(ServiceError.UNAUTHORISED.getErrorMessage());
    }

    @Test
    void shouldReturn400WhenProjectHeaderMissing() throws Exception {
      int aiPort = startFakeAi(resp -> resp.setStatusCode(200).end());
      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      String token = jwtService().generateAccessToken("u1", "a@b.com", "N");
      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var resp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("Authorization", "Bearer " + token)
              .rxSendBuffer(Buffer.buffer("{}"))
              .timeout(10, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(resp.statusCode()).isEqualTo(400);
      assertThat(
              new JsonObject(resp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("message"))
          .isEqualTo(ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS.getErrorMessage());
    }

    @Test
    void shouldReturn403WhenOpenFgaDenies() throws Exception {
      when(
              openFgaService.checkPermission(
                  eq("u1"),
                  eq(Constants.PERMISSION_CAN_VIEW),
                  eq(Constants.RESOURCE_TYPE_PROJECT),
                  eq("p9")))
          .thenReturn(Single.just(false));

      int aiPort = startFakeAi(resp -> resp.setStatusCode(200).end());
      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      String token = jwtService().generateAccessToken("u1", "a@b.com", "N");
      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var resp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("Authorization", "Bearer " + token)
              .putHeader("X-Project-ID", "p9")
              .rxSendBuffer(Buffer.buffer("{}"))
              .timeout(10, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(resp.statusCode()).isEqualTo(403);
      assertThat(
              new JsonObject(resp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("message"))
          .isEqualTo(ServiceError.FORBIDDEN.getErrorMessage());
    }
  }

  @Nested
  class UpstreamProxy {

    @Test
    void shouldStreamSseChunksWhenUpstreamReturns2xx() throws Exception {
      when(
              openFgaService.checkPermission(
                  any(),
                  eq(Constants.PERMISSION_CAN_VIEW),
                  eq(Constants.RESOURCE_TYPE_PROJECT),
                  any()))
          .thenReturn(Single.just(true));

      int aiPort =
          startFakeAi(
              resp ->
                  resp.setStatusCode(200)
                      .putHeader(
                          Constants.HEADER_CONTENT_TYPE, Constants.CONTENT_TYPE_TEXT_EVENT_STREAM)
                      .end("data: {\"t\":1}\n\n"));

      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      String token = jwtService().generateAccessToken("u1", "a@b.com", "N");
      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var httpResp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("Authorization", "Bearer " + token)
              .putHeader("X-Project-ID", "p1")
              .rxSendBuffer(Buffer.buffer("{\"q\":\"hi\"}"))
              .timeout(15, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(httpResp.statusCode()).isEqualTo(200);
      assertThat(httpResp.getHeader(Constants.HEADER_CONTENT_TYPE))
          .contains(Constants.CONTENT_TYPE_TEXT_EVENT_STREAM);
      assertThat(httpResp.getHeader(Constants.HEADER_X_ACCEL_BUFFERING))
          .isEqualTo(Constants.SSE_PROXY_X_ACCEL_BUFFERING);
      assertThat(httpResp.bodyAsString()).contains("data:").contains("\"t\":1");
    }

    @Test
    void shouldReturnJsonWhenUpstreamReturns5xx() throws Exception {
      when(
              openFgaService.checkPermission(
                  any(),
                  eq(Constants.PERMISSION_CAN_VIEW),
                  eq(Constants.RESOURCE_TYPE_PROJECT),
                  any()))
          .thenReturn(Single.just(true));

      int aiPort =
          startFakeAi(
              resp ->
                  resp.setStatusCode(500)
                      .putHeader("Content-Type", "application/json")
                      .end("{\"upstream\":true}"));

      ApplicationConfig appConfig = new ApplicationConfig();
      appConfig.setAiServiceUrl("http://127.0.0.1:" + aiPort);
      SharedDataUtils.put(coreVertx, appConfig);
      JsonObject wc =
          new JsonObject()
              .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
              .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
              .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "4");
      SharedDataUtils.put(
          coreVertx, AiStreamingHttpClient.create(coreVertx, wc), Constants.HTTP_CLIENT_AI_STREAMING);
      bindGuice(jwtService());

      String token = jwtService().generateAccessToken("u1", "a@b.com", "N");
      ExposeRouter rv = new ExposeRouter(rxVertx);
      int pulsePort = startPulse(routerFromVerticle(rv));

      var httpResp =
          WebClient.create(rxVertx)
              .post(pulsePort, "127.0.0.1", "/v1/ai/run_sse")
              .putHeader("Authorization", "Bearer " + token)
              .putHeader("X-Project-ID", "p1")
              .rxSendBuffer(Buffer.buffer("{}"))
              .timeout(15, TimeUnit.SECONDS)
              .blockingGet();

      assertThat(httpResp.statusCode()).isEqualTo(ServiceError.AI_PROXY_BAD_GATEWAY.getHttpStatusCode());
      assertThat(httpResp.getHeader("Content-Type")).contains("application/json");
      assertThat(
              new JsonObject(httpResp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("message"))
          .isEqualTo(ServiceError.AI_PROXY_BAD_GATEWAY.getErrorMessage());
      assertThat(
              new JsonObject(httpResp.bodyAsString())
                  .getJsonObject(Constants.ERROR_KEY)
                  .getString("code"))
          .isEqualTo(ServiceError.AI_PROXY_BAD_GATEWAY.getErrorCode());
    }
  }
}
