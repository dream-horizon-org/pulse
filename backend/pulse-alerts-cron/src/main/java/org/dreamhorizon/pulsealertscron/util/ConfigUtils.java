package org.dreamhorizon.pulsealertscron.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ConfigUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static <T> T fromConfigFile(String configPathPattern, Class<T> clazz) {
        String environment = Optional.ofNullable(System.getProperty("app.environment"))
                .orElse(Optional.ofNullable(System.getenv("APP_ENVIRONMENT")).orElse("default"));
        
        String configPath = String.format(configPathPattern, environment);
        log.info("Loading configuration from: {}", configPath);

        try {
            // Load the specific config file
            Config config = ConfigFactory.load(configPath);
            
            // Render config as JSON (removes comments and converts HOCON to JSON)
            ConfigRenderOptions options = ConfigRenderOptions.concise().setJson(true);
            String json = config.root().render(options);
            
            log.debug("Parsed configuration JSON: {}", json);
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Error loading configuration from {}: {}", configPath, e.getMessage(), e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public static Config loadConfig(String configPath) {
        return ConfigFactory.load(configPath);
    }
}

