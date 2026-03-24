package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BatchSchedulerService {
    private final Vertx vertx;
    private final PulseServerApiClient apiClient;
    private final ApplicationConfig config;
    private final RedisService redisService;
    
    private Long dailyBatchTimerId;
    
    private static final long CHECK_INTERVAL_MS = 60_000; // Check every minute
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss'Z'");
    
    @Inject
    public BatchSchedulerService(Vertx vertx, ApplicationConfig config, RedisService redisService) {
        this.vertx = vertx;
        this.config = config;
        this.redisService = redisService;
        this.apiClient = new PulseServerApiClient(
            SharedDataUtils.get(vertx, WebClient.class), 
            config
        );
    }
    
    public void start() {
        if (!config.isBatchJobsEnabled()) {
            log.info("[start] Batch jobs are disabled in configuration");
            return;
        }
        
        log.info("[start] Starting Daily Batch Scheduler (schedule time: {} UTC)", 
                 config.getBatchScheduleTime());
        
        // Start timer that checks every minute if it's time to run daily jobs
        this.dailyBatchTimerId = vertx.setPeriodic(CHECK_INTERVAL_MS, id -> {
            checkAndExecuteDailyJobs();
        });
        
        log.info("[start] Daily Batch Scheduler started with timer ID: {}", dailyBatchTimerId);
    }
    
    public void stop() {
        log.info("[stop] Stopping Daily Batch Scheduler");
        
        if (dailyBatchTimerId != null) {
            vertx.cancelTimer(dailyBatchTimerId);
            log.info("[stop] Cancelled daily batch timer: {}", dailyBatchTimerId);
            dailyBatchTimerId = null;
        }
        
        log.info("[stop] Daily Batch Scheduler stopped successfully");
    }
    
    private void checkAndExecuteDailyJobs() {
        try {
            LocalDateTime nowUTC = LocalDateTime.now(ZoneOffset.UTC);
            LocalDate today = nowUTC.toLocalDate();
            LocalTime currentTime = nowUTC.toLocalTime();
            LocalTime scheduleTime = LocalTime.parse(config.getBatchScheduleTime());
            
            // Check if it's time to run using Redis state
            shouldExecuteToday(today, currentTime, scheduleTime)
                .subscribe(
                    shouldExecute -> {
                        if (shouldExecute) {
                            log.info("[checkAndExecuteDailyJobs] Daily batch job execution time reached: {} UTC", nowUTC);
                            executeDailyBatchJobs(today);
                        }
                    },
                    error -> log.error("[checkAndExecuteDailyJobs] Error checking daily batch job schedule", error)
                );
        } catch (Exception e) {
            log.error("[checkAndExecuteDailyJobs] Error checking daily batch job schedule", e);
        }
    }
    
    private io.reactivex.rxjava3.core.Single<Boolean> shouldExecuteToday(LocalDate today, LocalTime currentTime, LocalTime scheduleTime) {
        String todayString = today.format(DATE_FORMATTER);
        
        return redisService.isBatchJobInProgress()
            .flatMap(jobInProgress -> {
                if (jobInProgress) {
                    log.debug("[shouldExecuteToday] Job already in progress, skipping");
                    return io.reactivex.rxjava3.core.Single.just(false);
                }
                
                return redisService.getLastBatchExecutionDate()
                    .map(lastExecutionDate -> {
                        // Don't execute if we already ran today
                        if (todayString.equals(lastExecutionDate)) {
                            log.debug("[shouldExecuteToday] Job already executed today: {}", today);
                            return false;
                        }
                        
                        // Check if current time has passed the schedule time
                        // Use a 5-minute window to account for service restarts or delays
                        LocalTime windowStart = scheduleTime;
                        LocalTime windowEnd = scheduleTime.plusMinutes(5);
                        
                        boolean inWindow = !currentTime.isBefore(windowStart) && !currentTime.isAfter(windowEnd);
                        
                        if (inWindow) {
                            log.info("[shouldExecuteToday] Current time {} is within execution window [{} - {}]", 
                                    currentTime, windowStart, windowEnd);
                        }
                        
                        return inWindow;
                    });
            });
    }
    
    private void executeDailyBatchJobs(LocalDate executionDate) {
        String executionDateString = executionDate.format(DATE_FORMATTER);
        String startedAt = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
        long startTime = System.currentTimeMillis();
        
        log.info("[executeDailyBatchJobs] Executing daily batch jobs for date: {}", executionDate);
        
        // Set job in progress flag first
        redisService.setBatchJobInProgress(true)
            .andThen(
                // Execute jobs sequentially with delay between them
                apiClient.triggerFunnelBatch()
                    .delay(5, TimeUnit.SECONDS) // 5 second delay between jobs
                    .flatMap(unused -> apiClient.triggerJourneyBatch())
                    .delay(5, TimeUnit.SECONDS)
                    .flatMap(unused -> apiClient.triggerEventsBatch())
            )
            .subscribe(
                unused -> {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Success: Update both execution date and clear progress flag
                    Completable.mergeArray(
                        redisService.setLastBatchExecutionDate(executionDateString),
                        redisService.setBatchJobInProgress(false),
                        redisService.saveBatchJobHistory(executionDateString, startedAt, "SUCCESS", duration)
                    ).subscribe(
                        () -> log.info("[executeDailyBatchJobs] All daily batch jobs completed successfully for date: {} (duration: {}ms)", 
                                      executionDate, duration),
                        error -> log.error("[executeDailyBatchJobs] Error updating Redis state after successful execution", error)
                    );
                },
                error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // Failure: Only clear progress flag, don't update execution date (allows retry)
                    Completable.mergeArray(
                        redisService.setBatchJobInProgress(false),
                        redisService.saveBatchJobHistory(executionDateString, startedAt, "FAILED", duration)
                    ).subscribe(
                        () -> log.error("[executeDailyBatchJobs] Daily batch jobs failed for date: {} (duration: {}ms), will retry within window", 
                                       executionDate, duration, error),
                        redisError -> log.error("[executeDailyBatchJobs] Error updating Redis state after failed execution", redisError)
                    );
                }
            );
    }
}