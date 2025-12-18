package org.dreamhorizon.pulsealertscron.guice;

import com.dream11.rest.ClassInjector;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GuiceInjector implements ClassInjector {

  private static GuiceInjector guiceInjector;
  private final Injector injector;

  private GuiceInjector(List<Module> modules) {
    injector = Guice.createInjector(modules);
  }

  public static synchronized void initialize(List<Module> modules) {
    if (guiceInjector != null) {
      log.warn("GuiceInjector already initialized");
      return;
    }
    guiceInjector = new GuiceInjector(modules);
    log.info("GuiceInjector initialized with {} modules", modules.size());
  }

  public static GuiceInjector getGuiceInjector() {
    if (guiceInjector == null) {
      throw new IllegalStateException("GuiceInjector not initialized");
    }
    return guiceInjector;
  }

  public static Injector getInjector() {
    if (guiceInjector == null) {
      throw new IllegalStateException("GuiceInjector not initialized");
    }
    return guiceInjector.injector;
  }

  @Override
  public <T> T getInstance(Class<T> clazz) {
    Objects.requireNonNull(injector, "injector is null, initialize first");
    return injector.getInstance(clazz);
  }
}

