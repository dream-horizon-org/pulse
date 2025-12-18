package org.dreamhorizon.pulsealertscron.util;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.keyvalue.MultiKey;

@Slf4j
public final class SharedDataUtils {
  private static final String SHARED_DATA = "sharedData";
  private static final String DEFAULT_NAME = "default";

  private SharedDataUtils() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  public static <T> T getOrCreateInstance(io.vertx.core.Vertx vertx, MultiKey key, Supplier<T> supplier) {
    LocalMap<MultiKey, ThreadSafe<T>> sharedDataMap = vertx.sharedData().getLocalMap(SHARED_DATA);
    return sharedDataMap.computeIfAbsent(key, k -> new ThreadSafe<>(supplier.get())).object();
  }

  public static <T> void put(io.vertx.core.Vertx vertx, T instance) {
    put(vertx, instance, DEFAULT_NAME);
  }

  public static <T> void put(io.vertx.core.Vertx vertx, T instance, String name) {
    getOrCreateInstance(vertx, getKey(instance.getClass(), name), () -> instance);
  }

  public static <T> T get(io.vertx.core.Vertx vertx, Class<T> clazz) {
    return get(vertx, clazz, DEFAULT_NAME);
  }

  public static <T> T get(Vertx vertx, Class<T> clazz, String name) {
    return getOrCreateInstance(
        vertx,
        getKey(clazz, name),
        () -> {
          throw new NoSuchElementException("Cannot find default instance of " + clazz.getName());
        });
  }

  record ThreadSafe<T>(@Getter T object) implements Shareable {
  }

  private static MultiKey getKey(Class<?> clazz, String name) {
    return new MultiKey(clazz.getName(), name);
  }
}
