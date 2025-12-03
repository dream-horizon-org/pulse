package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseReadClient;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseWriteClient;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.service.IAnalyticalStoreClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

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
