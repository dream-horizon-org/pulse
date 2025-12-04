package org.dreamhorizon.pulsealertscron.util;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SharedDataUtils {

    private static final String SHARED_DATA_MAP_NAME = "shared-data-map";

    public static <T> void put(Vertx vertx, T instance) {
        put(vertx, instance.getClass().getName(), instance);
    }

    public static <T> void put(Vertx vertx, String key, T instance) {
        LocalMap<String, Object> map = vertx.sharedData().getLocalMap(SHARED_DATA_MAP_NAME);
        map.put(key, instance);
        log.debug("Put instance in shared data with key: {}", key);
    }

    public static <T> void put(Vertx vertx, Class<T> clazz) {
        put(vertx, clazz.getName(), clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Vertx vertx, Class<T> clazz) {
        return get(vertx, clazz.getName());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Vertx vertx, String key) {
        LocalMap<String, Object> map = vertx.sharedData().getLocalMap(SHARED_DATA_MAP_NAME);
        T instance = (T) map.get(key);
        if (instance == null) {
            log.warn("No instance found in shared data with key: {}", key);
        }
        return instance;
    }

    public static void remove(Vertx vertx, String key) {
        LocalMap<String, Object> map = vertx.sharedData().getLocalMap(SHARED_DATA_MAP_NAME);
        map.remove(key);
        log.debug("Removed instance from shared data with key: {}", key);
    }

    public static void clear(Vertx vertx) {
        LocalMap<String, Object> map = vertx.sharedData().getLocalMap(SHARED_DATA_MAP_NAME);
        map.clear();
        log.debug("Cleared all instances from shared data");
    }
}

