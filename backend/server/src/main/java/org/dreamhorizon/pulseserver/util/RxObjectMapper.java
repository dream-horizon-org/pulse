package org.dreamhorizon.pulseserver.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RxObjectMapper {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final ExecutorService threadPool = Executors.newFixedThreadPool(20);
  private static final Scheduler vertxEventLoopScheduler = Utils.fromVertxEventLoop();

  public <T> Single<T> readValue(String content, Class<T> valueType) {
    return Single.fromCallable(() -> objectMapper.readValue(content, valueType))
        .subscribeOn(Schedulers.from(threadPool))
        .observeOn(vertxEventLoopScheduler)
        .doOnError(err -> log.error("Error while parsing content to {}", valueType, err));
  }

  public Single<byte[]> writeValueAsBytes(Object obj) {
    return Single.fromCallable(() -> objectMapper.writeValueAsBytes(obj))
        .subscribeOn(Schedulers.from(threadPool))
        .observeOn(vertxEventLoopScheduler)
        .doOnError(err -> log.error("Error while converting value to json bytes", err));
  }

  public <T> Single<T> convertValue(Object content, Class<T> valueType) {
    return Single.fromCallable(() -> objectMapper.convertValue(content, valueType))
        .subscribeOn(Schedulers.from(threadPool))
        .observeOn(vertxEventLoopScheduler)
        .doOnError(err -> log.error("Error while converting content to {}", valueType, err));
  }

  public <T> Single<T> convertValue(Object content, TypeReference<T> toValueTypeRef) {
    return Single.fromCallable(() -> objectMapper.convertValue(content, toValueTypeRef))
        .subscribeOn(Schedulers.from(threadPool))
        .observeOn(vertxEventLoopScheduler)
        .doOnError(err -> log.error("Error while converting content to {}", toValueTypeRef, err));
  }

  @SuppressWarnings("unchecked")
  public <T> Single<T> convertValue(Object fromValue, JavaType toValueType) {
    return Single.fromCallable(() -> (T) objectMapper.convertValue(fromValue, toValueType))
        .subscribeOn(Schedulers.from(threadPool))
        .observeOn(vertxEventLoopScheduler)
        .doOnError(err -> log.error("Error while converting content to {}", toValueType, err));
  }

  public TypeFactory getTypeFactory() {
    return objectMapper.getTypeFactory();
  }
}
