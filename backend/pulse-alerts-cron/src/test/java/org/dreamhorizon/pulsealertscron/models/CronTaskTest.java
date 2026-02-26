package org.dreamhorizon.pulsealertscron.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Test for CronTask model
 */
class CronTaskTest {


  @Test
  void testCronTaskSettersGetters() {
    CronTask task = new CronTask();
    task.setId(2);
    task.setUrl("http://test.com");

    assertEquals(2, task.getId(), "ID should match");
    assertEquals("http://test.com", task.getUrl(), "URL should match");
  }
}

