package in.horizonos.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import in.horizonos.pulseserver.client.chclient.ClickhouseQueryService;
import in.horizonos.pulseserver.client.chclient.ClickhouseReadClient;
import in.horizonos.pulseserver.client.chclient.ClickhouseWriteClient;
import in.horizonos.pulseserver.config.ClickhouseConfig;
import in.horizonos.pulseserver.dto.response.GetRawUserEventsResponseDto;
import in.horizonos.pulseserver.service.IAnalyticalStoreClient;
import in.horizonos.pulseserver.vertx.SharedDataUtils;
import io.vertx.core.Vertx;

public class UploadInteractionDetailModule extends AbstractModule {


  private final Vertx vertx;

  public UploadInteractionDetailModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(ClickhouseReadClient.class).toProvider(() -> new ClickhouseReadClient(SharedDataUtils.get(vertx, ClickhouseConfig.class)))
        .in(Singleton.class);
    bind(ClickhouseWriteClient.class).toProvider(() -> new ClickhouseWriteClient((SharedDataUtils.get(vertx, ClickhouseConfig.class))))
        .in(Singleton.class);
    bind(new TypeLiteral<IAnalyticalStoreClient<GetRawUserEventsResponseDto>>() {
    }).to(ClickhouseQueryService.class);
  }
}
