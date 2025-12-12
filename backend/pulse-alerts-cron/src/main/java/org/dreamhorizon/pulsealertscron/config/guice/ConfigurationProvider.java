package org.dreamhorizon.pulsealertscron.config.guice;

import com.google.inject.Provider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;

public class ConfigurationProvider implements Provider<Config> {

  @Override
  public Config get() {
    String environment = Optional.ofNullable(System.getProperty("app.environment")).orElse("default");
    String configFile = "config/application/application-" + environment + ".conf";
    return ConfigFactory.load(configFile)
        .withFallback(ConfigFactory.load("config/application/application-default.conf"));
  }
}

