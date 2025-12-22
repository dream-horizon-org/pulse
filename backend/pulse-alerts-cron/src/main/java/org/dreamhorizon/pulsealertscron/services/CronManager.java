package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.dto.response.CronManagerDto;
import org.dreamhorizon.pulsealertscron.models.CronTask;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class CronManager {
  final Vertx vertx;
  final WebClient webClient;
  private static final ConcurrentHashMap<Integer, CopyOnWriteArrayList<CronTask>> cronGroups = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Integer, Long> timerIds = new ConcurrentHashMap<>();

  // Retry configuration
  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
  private static final long REQUEST_TIMEOUT_MS = 30000; // 30 seconds

  public Single<CronManagerDto> addCronTask(Integer id, String url, Integer interval) {
    try {
      CronTask newTask = new CronTask(id, url);
      cronGroups.computeIfAbsent(interval, k -> {
        startTimerForInterval(k);
        return new CopyOnWriteArrayList<>();
      }).add(newTask);

      log.info("cron added: " + id + " for interval: " + interval);
      return Single.just(CronManagerDto.builder().status("success").build());
    } catch (Exception e) {
      return Single.just(CronManagerDto.builder().status("failure").failureReason(e.getMessage()).build());
    }
  }

  public void modifyCronTask(Integer id, String newUrl, Integer newInterval, Integer oldInterval) {
    removeCronTask(id, oldInterval);
    addCronTask(id, newUrl, newInterval).subscribe();
  }

  public void removeCronTask(Integer id, Integer interval) {
    CopyOnWriteArrayList<CronTask> tasks = cronGroups.get(interval);
    if (tasks != null) {
      tasks.removeIf(task -> task.getId().equals(id));
      if (tasks.isEmpty()) {
        cronGroups.remove(interval);
        cancelTimerForInterval(interval);
      }
    }
  }

  private void startTimerForInterval(Integer interval) {
    long timerId = vertx.setPeriodic(interval * 1000, id -> {
      log.info("Executing tasks for interval: " + interval);
      executeTasksForInterval(interval);
    });

    timerIds.put(interval, timerId);
  }

  private void cancelTimerForInterval(Integer interval) {
    Long timerId = timerIds.remove(interval);
    if (timerId != null) {
      vertx.cancelTimer(timerId);
      System.out.println("Cancelled timer for interval: " + interval + " seconds");
    }
  }

  private void executeTasksForInterval(Integer interval) {
    List<CronTask> tasks = cronGroups.get(interval);
    if (tasks != null) {
      tasks.forEach(task -> {
        log.info("Executing task: {}", task.getId());
        triggerEvaluation(task.getUrl());
      });
    } else {
      log.info("No tasks found for interval: {}", interval);
      vertx.cancelTimer(timerIds.get(interval));
    }
  }

  private void triggerEvaluation(String evaluationUrl) {
    log.info("Triggering evaluation for url: {}", evaluationUrl);

    AtomicInteger attemptCounter = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    makeRequestWithRetry(evaluationUrl, attemptCounter, startTime)
        .subscribe(
            response -> {
              long duration = System.currentTimeMillis() - startTime;
              log.info("✅ Evaluation successful for url: {} | Status: {} | Duration: {}ms | Attempts: {}",
                  evaluationUrl,
                  response.statusCode(),
                  duration,
                  attemptCounter.get());
            },
            error -> {
              long duration = System.currentTimeMillis() - startTime;
              log.error("❌ Evaluation failed for url: {} | Duration: {}ms | Attempts: {} | Error: {}",
                  evaluationUrl,
                  duration,
                  attemptCounter.get(),
                  error.getMessage());
            }
        );
  }

  private Single<HttpResponse<Buffer>> makeRequestWithRetry(
      String url,
      AtomicInteger attemptCounter,
      long startTime) {

    return Single.defer(() -> {
      int currentAttempt = attemptCounter.incrementAndGet();

      if (currentAttempt > 1) {
        log.info("🔄 Retry attempt {} for url: {}", currentAttempt, url);
      }

      return webClient
          .getAbs(url)
          .timeout(REQUEST_TIMEOUT_MS)
          .rxSend()
          .flatMap(response -> {
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
              return Single.just(response);
            }

            if (statusCode >= 400 && statusCode < 500) {
              log.warn("⚠️ Client error {} for url: {} - Not retrying", statusCode, url);
              return Single.just(response);
            }

            if (statusCode >= 500) {
              String errorMsg = String.format("Server error %d for url: %s", statusCode, url);
              log.warn("⚠️ {} - Will retry if attempts remaining", errorMsg);
              return Single.error(new RuntimeException(errorMsg));
            }

            return Single.just(response);
          })
          .onErrorResumeNext(error -> {
            if (currentAttempt < MAX_RETRY_ATTEMPTS) {
              long delayMs = calculateBackoffDelay(currentAttempt);

              log.warn("⏳ Retrying after {}ms delay (attempt {}/{}) for url: {} | Error: {}",
                  delayMs,
                  currentAttempt,
                  MAX_RETRY_ATTEMPTS,
                  url,
                  error.getMessage());

              return Single.timer(delayMs, TimeUnit.MILLISECONDS)
                  .flatMap(tick -> makeRequestWithRetry(url, attemptCounter, startTime));
            } else {
              log.error("❌ Max retry attempts ({}) exhausted for url: {}", MAX_RETRY_ATTEMPTS, url);
              return Single.error(error);
            }
          });
    });
  }

  private long calculateBackoffDelay(int attempt) {
    return INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
  }
}

