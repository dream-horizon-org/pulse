package org.dreamhorizon.pulsealertscron.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static JsonObject jsonFrom(String key, String value) {
        return new JsonObject().put(key, value);
    }

    public static JsonObject jsonFrom(String key, Object value) {
        return new JsonObject().put(key, value);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    public static JsonObject toJsonObject(Object object) {
        String json = toJson(object);
        return new JsonObject(json);
    }
}

