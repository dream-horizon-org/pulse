package org.dreamhorizon.pulses3archiver;

import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulses3archiver.module.ArchiverModule;
import org.dreamhorizon.pulses3archiver.service.S3UploadService;

public class MainModule extends ArchiverModule {

  public MainModule(Vertx vertx) {
    super(vertx);
  }

  @Override
  protected void configure() {
    super.configure();
    bind(S3UploadService.class).in(Singleton.class);
  }
}
