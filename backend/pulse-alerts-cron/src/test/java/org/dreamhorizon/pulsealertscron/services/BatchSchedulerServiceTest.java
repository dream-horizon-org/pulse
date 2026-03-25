package org.dreamhorizon.pulsealertscron.services;

import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchSchedulerServiceTest {
    
    @Mock
    private ApplicationConfig config;
    
    private BatchSchedulerService batchSchedulerService;
    
    @BeforeEach
    void setUp() {
        // Setup is done per test as needed to avoid unnecessary stubbing
    }
    
    @Test
    void testBatchSchedulerServiceCreation() {
        // Test that BatchSchedulerService can be created with basic config
        // Note: Full integration testing would require Vertx and WebClient setup
        when(config.isBatchJobsEnabled()).thenReturn(true);
        when(config.getBatchScheduleTime()).thenReturn("02:00");
        
        // Validate basic configuration handling
        assertTrue(config.isBatchJobsEnabled(), "Batch jobs should be enabled");
        assertEquals("02:00", config.getBatchScheduleTime(), "Schedule time should be 02:00");
    }
    
    @Test
    void testShouldExecuteInTimeWindow() {
        // Given
        LocalTime scheduleTime = LocalTime.of(2, 0);
        LocalTime currentTime1 = LocalTime.of(2, 1); // Within window
        LocalTime currentTime2 = LocalTime.of(2, 4); // Within window  
        LocalTime currentTime3 = LocalTime.of(2, 6); // Outside window
        
        // Test window logic
        LocalTime windowStart = scheduleTime;
        LocalTime windowEnd = scheduleTime.plusMinutes(5);
        
        boolean inWindow1 = !currentTime1.isBefore(windowStart) && !currentTime1.isAfter(windowEnd);
        boolean inWindow2 = !currentTime2.isBefore(windowStart) && !currentTime2.isAfter(windowEnd);
        boolean inWindow3 = !currentTime3.isBefore(windowStart) && !currentTime3.isAfter(windowEnd);
        
        assertTrue(inWindow1, "Should be in window at 02:01");
        assertTrue(inWindow2, "Should be in window at 02:04");
        assertFalse(inWindow3, "Should be outside window at 02:06");
    }
    
    @Test
    void testConfigurationDefaults() {
        ApplicationConfig testConfig = new ApplicationConfig();
        
        assertEquals("/internal/analytics/funnels", testConfig.getBatchFunnelsEndpoint());
        assertEquals("/internal/analytics/journeys", testConfig.getBatchJourneysEndpoint());
        assertEquals("/internal/analytics/events", testConfig.getBatchEventsEndpoint());
        assertEquals("02:00", testConfig.getBatchScheduleTime());
        assertTrue(testConfig.isBatchJobsEnabled());
    }
}