package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.util.SharedDataUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BatchSchedulerService {
    private final Vertx vertx;
    private final PulseServerApiClient apiClient;
    private final ApplicationConfig config;
    
    private Long dailyBatchTimerId;
    
    private static final long CHECK_INTERVAL_MS = 60_000; // Check every minute
    
    @Inject
    public BatchSchedulerService(Vertx vertx, ApplicationConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.apiClient = new PulseServerApiClient(
            SharedDataUtils.get(vertx.getDelegate(), WebClient.class), 
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
            LocalTime currentTime = nowUTC.toLocalTime();
            LocalTime scheduleTime = LocalTime.parse(config.getBatchScheduleTime());
            
            // Check if current time is within the execution window
            if (shouldExecuteNow(currentTime, scheduleTime)) {
                log.info("[checkAndExecuteDailyJobs] Daily batch job execution time reached: {} UTC", nowUTC);
                executeDailyBatchJobs();
            }
        } catch (Exception e) {
            log.error("[checkAndExecuteDailyJobs] Error checking daily batch job schedule", e);
        }
    }
    
    private boolean shouldExecuteNow(LocalTime currentTime, LocalTime scheduleTime) {
        // Check if current time is within the execution window
        // Use a 5-minute window to account for service restarts or delays
        LocalTime windowStart = scheduleTime;
        LocalTime windowEnd = scheduleTime.plusMinutes(5);
        
        boolean inWindow = !currentTime.isBefore(windowStart) && !currentTime.isAfter(windowEnd);
        
        if (inWindow) {
            log.info("[shouldExecuteNow] Current time {} is within execution window [{} - {}]", 
                    currentTime, windowStart, windowEnd);
        }
        
        return inWindow;
    }
    
    private void executeDailyBatchJobs() {
        LocalDateTime nowUTC = LocalDateTime.now(ZoneOffset.UTC);
        long startTime = System.currentTimeMillis();
        
        log.info("[executeDailyBatchJobs] Executing daily batch jobs at: {}", nowUTC);
        
        // Execute jobs sequentially with delay between them
        apiClient.triggerFunnelBatch()
            .delay(5, TimeUnit.SECONDS) // 5 second delay between jobs
            .andThen(apiClient.triggerJourneyBatch())
            .delay(5, TimeUnit.SECONDS)
            .andThen(apiClient.triggerEventsBatch())
            .subscribe(
                () -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[executeDailyBatchJobs] All daily batch jobs completed successfully (duration: {}ms)", duration);
                },
                error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[executeDailyBatchJobs] Daily batch jobs failed (duration: {}ms)", duration, error);
                }
            );
    }
}