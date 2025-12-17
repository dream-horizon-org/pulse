package org.dreamhorizon.pulsealertscron.util;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class VertxUtil {

  private static final String SHARED_DATA_MAP_NAME = "__vertx.sharedDataUtils";
  private static final String CLASS_PREFIX = "__class.";
  private static final String SHARED_DATA_DEFAULT_KEY = "__default.";

  private VertxUtil() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static <T> T getOrCreateSharedData(Vertx vertx, String name, Supplier<T> supplier) {
    LocalMap<String, ThreadSafe<T>> singletons =
        vertx.sharedData().getLocalMap(SHARED_DATA_MAP_NAME);
    // LocalMap is internally backed by a ConcurrentMap
    return singletons.computeIfAbsent(name, k -> new ThreadSafe<>(supplier.get())).object();
  }

  public static <T> void setInstanceInSharedData(Vertx vertx, T instance) {
    setInstanceInSharedData(vertx, instance, SHARED_DATA_DEFAULT_KEY);
  }
  
  public static <T> void setInstanceInSharedData(Vertx vertx, T instance, String key) {
    log.debug(
        "setInstanceInSharedData: vertx instance {} is setting type : {} for instance: {}  in key {}",
        System.identityHashCode(vertx),
        instance.getClass().getName(),
        System.identityHashCode(instance),
        key);
    getOrCreateSharedData(
        vertx, CLASS_PREFIX + instance.getClass().getName() + key, () -> instance);
  }
  
  public static <T> T getInstanceFromSharedData(Vertx vertx, Class<T> clazz) {
    return getInstanceFromSharedData(vertx, clazz, SHARED_DATA_DEFAULT_KEY);
  }
  
  public static <T> T getInstanceFromSharedData(Vertx vertx, Class<T> clazz, String key) {
    log.debug(
        "getInstanceFromSharedData: vertx instance {} is getting type : {}  in key {}",
        System.identityHashCode(vertx),
        clazz.getName(),
        key);
    return getOrCreateSharedData(
        vertx,
        CLASS_PREFIX + clazz.getName() + key,
        () -> {
          throw new NoSuchElementException("Cannot find default instance of " + clazz.getName());
        });
  }

  record ThreadSafe<T>(@Getter T object) implements Shareable {
  }
}

