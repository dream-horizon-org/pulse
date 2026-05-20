package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.errorgrouping.archive.OtelArchiveS3UploadService;
import org.dreamhorizon.pulseserver.errorgrouping.archive.StackTraceArchiveConfig;
import org.dreamhorizon.pulseserver.errorgrouping.archive.StackTraceArchiveService;

public class StackTraceArchiveModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(StackTraceArchiveConfig.class).toInstance(StackTraceArchiveConfig.fromEnvironment());
    bind(OtelArchiveS3UploadService.class).in(Singleton.class);
    bind(StackTraceArchiveService.class).in(Singleton.class);
  }
}
