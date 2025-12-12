package org.dreamhorizon.pulsealertscron.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GuiceInjector {

    private static Injector injector;

    public static void initialize(List<Module> modules) {
        if (injector != null) {
            log.warn("GuiceInjector already initialized");
            return;
        }
        injector = Guice.createInjector(modules);
        log.info("GuiceInjector initialized with {} modules", modules.size());
    }

    public static Injector getInjector() {
        if (injector == null) {
            throw new IllegalStateException("GuiceInjector not initialized");
        }
        return injector;
    }

    public static <T> T getInstance(Class<T> clazz) {
        return getInjector().getInstance(clazz);
    }
}

