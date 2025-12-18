package org.dreamhorizon.pulsealertscron.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for CronTask model
 */
class CronTaskTest {

    @Test
    void testCronTaskCreation() {
        CronTask task = new CronTask(1, "http://example.com/api");
        assertNotNull(task, "CronTask should be created");
        assertEquals(1, task.getId(), "ID should match");
        assertEquals("http://example.com/api", task.getUrl(), "URL should match");
    }

    @Test
    void testCronTaskSettersGetters() {
        CronTask task = new CronTask();
        task.setId(2);
        task.setUrl("http://test.com");
        
        assertEquals(2, task.getId(), "ID should match");
        assertEquals("http://test.com", task.getUrl(), "URL should match");
    }
}

