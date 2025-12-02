package in.horizonos.pulseserver.util;

import io.reactivex.rxjava3.core.CompletableTransformer;
import io.reactivex.rxjava3.core.SingleTransformer;
import java.util.concurrent.atomic.AtomicLong;
import lombok.val;
import org.slf4j.Logger;

public final class CompletableUtils {

  /**
   * Operator which adds debug logs to a Maybe.
   */
  public static CompletableTransformer applyDebugLogs(Logger log, String logPrefix) {
    AtomicLong startTime = new AtomicLong();
    return observable -> observable
        .doOnSubscribe(disposable -> {
          startTime.set(System.currentTimeMillis());
          log.debug("{} Subscribed", logPrefix);
        })
        .doOnComplete(() -> {
          long elapsedTime = System.currentTimeMillis() - startTime.get();
          log.debug("{} Completed after {}ms", logPrefix, elapsedTime);
        })
        .doOnError(err -> {
          long elapsedTime = System.currentTimeMillis() - startTime.get();
          log.error("{} Error after {}ms {}", logPrefix, elapsedTime, err.getMessage(), err);
        });
  }

  public static CompletableTransformer applyDebugLogs(Logger log) {
    val logPrefix = Thread.currentThread().getStackTrace()[3].getMethodName();
    return applyDebugLogs(log, logPrefix);
  }

  public static <T> SingleTransformer<T, T> applyDebugLogsSingle(Logger log, String logPrefix) {
    AtomicLong startTime = new AtomicLong();
    return single -> single
        .doOnSubscribe(disposable -> {
          startTime.set(System.currentTimeMillis());
          log.debug("{} Subscribed", logPrefix);
        })
        .doOnSuccess(result -> {
          long elapsedTime = System.currentTimeMillis() - startTime.get();
          log.debug("{} Received after {}ms {}", logPrefix, elapsedTime, result);
        })
        .doOnError(err -> {
          long elapsedTime = System.currentTimeMillis() - startTime.get();
          log.error("{} Error after {}ms {}", logPrefix, elapsedTime, err.getMessage(), err);
        });
  }

  public static <T> SingleTransformer<T, T> applyDebugLogsSingle(Logger log) {
    val logPrefix = Thread.currentThread().getStackTrace()[3].getMethodName();
    return applyDebugLogsSingle(log, logPrefix);
  }
}
