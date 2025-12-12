package org.dreamhorizon.pulsealertscron.module;

import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.config.guice.ConfigurationProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigModule extends AbstractModule {

    private final Vertx vertx;

    public ConfigModule(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    protected void configure() {
        bind(Config.class).toProvider(ConfigurationProvider.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public ApplicationConfig provideApplicationConfig(Config config) {
        try {
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
            ConfigRenderOptions options = ConfigRenderOptions.concise().setJson(true);
            String json = config.root().render(options);
            
            ApplicationConfig appConfig = objectMapper.readValue(json, ApplicationConfig.class);
            log.info("Loaded ApplicationConfig: pulseServerUrl={}", appConfig.getPulseServerUrl());
            return appConfig;
        } catch (Exception e) {
            log.error("Error creating ApplicationConfig", e);
            throw new RuntimeException("Failed to create ApplicationConfig", e);
        }
    }
}

