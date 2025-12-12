package org.dreamhorizon.pulsealertscron.util;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JsonUtils
 */
class JsonUtilsTest {

    @Test
    void testJsonFromWithStringValue() {
        JsonObject json = JsonUtils.jsonFrom("key", "value");
        assertNotNull(json, "JsonObject should not be null");
        assertEquals("value", json.getString("key"), "Value should match");
    }

    @Test
    void testJsonFromWithObjectValue() {
        JsonObject json = JsonUtils.jsonFrom("count", 42);
        assertNotNull(json, "JsonObject should not be null");
        assertEquals(42, json.getInteger("count"), "Value should match");
    }

    @Test
    void testToJsonAndFromJson() {
        TestObject obj = new TestObject("test", 123);
        String json = JsonUtils.toJson(obj);
        assertNotNull(json, "JSON string should not be null");
        assertTrue(json.contains("test"), "JSON should contain the name");
    }

    // Helper class for testing
    static class TestObject {
        public String name;
        public int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}

