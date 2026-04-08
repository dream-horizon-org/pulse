package org.dreamhorizon.pulseserver.service.configs.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link AttributeToDrop}.
 * Tests all Lombok-generated methods to ensure full coverage.
 */
class AttributeToDropTest {

  @Nested
  class TestCreation {

    @Test
    void shouldCreateWithNoArgs() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();
      assertNotNull(attributeToDrop);
      assertNull(attributeToDrop.getValues());
      assertNull(attributeToDrop.getCondition());
    }

    @Test
    void shouldCreateWithBuilder() {
      List<String> values = Arrays.asList("attr1", "attr2");
      EventFilter condition = EventFilter.builder()
          .name("testEvent")
          .scopes(Arrays.asList(Scope.logs))
          .sdks(Arrays.asList(Sdk.pulse_android_java))
          .build();

      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(values)
          .condition(condition)
          .build();

      assertEquals(values, attributeToDrop.getValues());
      assertEquals(condition, attributeToDrop.getCondition());
      assertEquals(2, attributeToDrop.getValues().size());
      assertEquals("attr1", attributeToDrop.getValues().get(0));
      assertEquals("attr2", attributeToDrop.getValues().get(1));
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      List<String> values = Arrays.asList("sensitiveAttr1", "sensitiveAttr2");
      EventFilter condition = EventFilter.builder()
          .name("http.request")
          .build();

      AttributeToDrop attributeToDrop = new AttributeToDrop(values, condition);

      assertEquals(values, attributeToDrop.getValues());
      assertEquals(condition, attributeToDrop.getCondition());
    }

    @Test
    void shouldCreateWithBuilderAndNullValues() {
      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(null)
          .condition(null)
          .build();

      assertNull(attributeToDrop.getValues());
      assertNull(attributeToDrop.getCondition());
    }

    @Test
    void shouldCreateWithBuilderAndEmptyValues() {
      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(new ArrayList<>())
          .build();

      assertNotNull(attributeToDrop.getValues());
      assertTrue(attributeToDrop.getValues().isEmpty());
    }
  }

  @Nested
  class TestSettersAndGetters {

    @Test
    void shouldSetAndGetValues() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();
      List<String> values = Arrays.asList("attr1", "attr2", "attr3");

      attributeToDrop.setValues(values);

      assertEquals(values, attributeToDrop.getValues());
      assertEquals(3, attributeToDrop.getValues().size());
    }

    @Test
    void shouldSetAndGetCondition() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();
      EventFilter condition = EventFilter.builder()
          .name("testEvent")
          .props(new ArrayList<>())
          .scopes(Arrays.asList(Scope.logs, Scope.traces))
          .sdks(Arrays.asList(Sdk.pulse_android_java, Sdk.pulse_ios_swift))
          .build();

      attributeToDrop.setCondition(condition);

      assertEquals(condition, attributeToDrop.getCondition());
      assertEquals("testEvent", attributeToDrop.getCondition().getName());
    }

    @Test
    void shouldSetNullValues() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();
      attributeToDrop.setValues(Arrays.asList("attr1"));

      attributeToDrop.setValues(null);

      assertNull(attributeToDrop.getValues());
    }

    @Test
    void shouldSetNullCondition() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();
      attributeToDrop.setCondition(EventFilter.builder().name("test").build());

      attributeToDrop.setCondition(null);

      assertNull(attributeToDrop.getCondition());
    }

    @Test
    void shouldUpdateValues() {
      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(Arrays.asList("old1", "old2"))
          .build();

      attributeToDrop.setValues(Arrays.asList("new1", "new2", "new3"));

      assertEquals(3, attributeToDrop.getValues().size());
      assertEquals("new1", attributeToDrop.getValues().get(0));
    }

    @Test
    void shouldUpdateCondition() {
      EventFilter oldCondition = EventFilter.builder().name("old").build();
      EventFilter newCondition = EventFilter.builder().name("new").build();

      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .condition(oldCondition)
          .build();

      attributeToDrop.setCondition(newCondition);

      assertEquals("new", attributeToDrop.getCondition().getName());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldBeEqualForSameValues() {
      List<String> values = Arrays.asList("attr1", "attr2");
      EventFilter condition = EventFilter.builder().name("event").build();

      AttributeToDrop attr1 = AttributeToDrop.builder()
          .values(values)
          .condition(condition)
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .values(values)
          .condition(condition)
          .build();

      assertEquals(attr1, attr2);
    }

    @Test
    void shouldHaveSameHashCodeForSameValues() {
      List<String> values = Arrays.asList("attr1", "attr2");
      EventFilter condition = EventFilter.builder().name("event").build();

      AttributeToDrop attr1 = AttributeToDrop.builder()
          .values(values)
          .condition(condition)
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .values(values)
          .condition(condition)
          .build();

      assertEquals(attr1.hashCode(), attr2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
      AttributeToDrop attr1 = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .values(Arrays.asList("attr2"))
          .build();

      assertNotEquals(attr1, attr2);
    }

    @Test
    void shouldNotBeEqualForDifferentConditions() {
      EventFilter condition1 = EventFilter.builder().name("event1").build();
      EventFilter condition2 = EventFilter.builder().name("event2").build();

      AttributeToDrop attr1 = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .condition(condition1)
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .condition(condition2)
          .build();

      assertNotEquals(attr1, attr2);
    }

    @Test
    void shouldBeEqualWhenBothFieldsNull() {
      AttributeToDrop attr1 = new AttributeToDrop();
      AttributeToDrop attr2 = new AttributeToDrop();

      assertEquals(attr1, attr2);
      assertEquals(attr1.hashCode(), attr2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenOneHasNullValues() {
      AttributeToDrop attr1 = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .values(null)
          .build();

      assertNotEquals(attr1, attr2);
    }

    @Test
    void shouldNotBeEqualWhenOneHasNullCondition() {
      EventFilter condition = EventFilter.builder().name("event").build();

      AttributeToDrop attr1 = AttributeToDrop.builder()
          .condition(condition)
          .build();
      AttributeToDrop attr2 = AttributeToDrop.builder()
          .condition(null)
          .build();

      assertNotEquals(attr1, attr2);
    }

    @Test
    void shouldBeEqualToItself() {
      AttributeToDrop attr = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .condition(EventFilter.builder().name("event").build())
          .build();

      assertEquals(attr, attr);
    }

    @Test
    void shouldNotBeEqualToNull() {
      AttributeToDrop attr = new AttributeToDrop();

      assertNotEquals(null, attr);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
      AttributeToDrop attr = new AttributeToDrop();

      assertFalse(attr.equals("string"));
    }

    @Test
    void shouldHandleCanEqual() {
      AttributeToDrop attr = new AttributeToDrop();

      assertTrue(attr.canEqual(new AttributeToDrop()));
      assertFalse(attr.canEqual("string"));
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldContainFieldValuesInToString() {
      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(Arrays.asList("sensitiveAttr1", "sensitiveAttr2"))
          .condition(EventFilter.builder().name("testEvent").build())
          .build();

      String toString = attributeToDrop.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("AttributeToDrop"));
      assertTrue(toString.contains("sensitiveAttr1"));
      assertTrue(toString.contains("sensitiveAttr2"));
    }

    @Test
    void shouldHaveToStringForEmptyObject() {
      AttributeToDrop attributeToDrop = new AttributeToDrop();

      String toString = attributeToDrop.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("AttributeToDrop"));
    }

    @Test
    void shouldHaveToStringWithNullFields() {
      AttributeToDrop attributeToDrop = AttributeToDrop.builder()
          .values(null)
          .condition(null)
          .build();

      String toString = attributeToDrop.toString();
      assertNotNull(toString);
    }
  }

  @Nested
  class TestBuilderToString {

    @Test
    void shouldHaveBuilderToString() {
      String builderToString = AttributeToDrop.builder()
          .values(Arrays.asList("attr1"))
          .condition(EventFilter.builder().name("event").build())
          .toString();

      assertNotNull(builderToString);
    }
  }
}
