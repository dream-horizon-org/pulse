package in.horizonos.pulseserver.verticle;

import com.dream11.rest.AbstractRestVerticle;
import com.dream11.rest.ClassInjector;
import in.horizonos.pulseserver.guice.GuiceInjector;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import io.vertx.rxjava3.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.rxjava3.ext.web.handler.StaticHandler;
import java.util.HashSet;
import java.util.Set;

public class RestVerticle extends AbstractRestVerticle {
  private static final String PACKAGE_NAME = "in.horizonos.pulseserver";


  protected RestVerticle(HttpServerOptions httpServerOptions) {
    super(PACKAGE_NAME, httpServerOptions);
  }

  @Override
  protected ClassInjector getInjector() {
    return GuiceInjector.getGuiceInjector();
  }

  @Override
  protected Router getRouter() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route().handler(ResponseContentTypeHandler.create());
    router.route().handler(StaticHandler.create());


    final Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add("x-requested-with");
    allowedHeaders.add("Access-Control-Allow-Origin");
    allowedHeaders.add("Access-Control-Allow-Methods");
    allowedHeaders.add("Access-Control-Allow-Headers");
    allowedHeaders.add("Access-Control-Allow-Credentials");
    allowedHeaders.add("origin");
    allowedHeaders.add("Content-Type");
    allowedHeaders.add("accept");
    allowedHeaders.add("X-PINGARUNER");
    allowedHeaders.add("Authorization");

    final Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    allowedMethods.add(HttpMethod.DELETE);
    allowedMethods.add(HttpMethod.PATCH);
    allowedMethods.add(HttpMethod.PUT);
    router.route().handler(CorsHandler.create()
        .addRelativeOrigin(".*.")
        .allowCredentials(true)
        .allowedMethods(allowedMethods)
        .allowedHeaders(allowedHeaders));

    return router;
  }
}
