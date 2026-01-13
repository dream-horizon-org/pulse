package org.dreamhorizon.pulseserver.service.configs.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AttributeValueTest {

  @Nested
  class TestAttributeValueCreation {

    @Test
    void shouldCreateWithNoArgs() {
      AttributeValue attributeValue = new AttributeValue();
      assertNotNull(attributeValue);
    }

    @Test
    void shouldCreateWithBuilder() {
      AttributeValue attributeValue = AttributeValue.builder()
          .name("testAttribute")
          .value("testValue")
          .build();

      assertEquals("testAttribute", attributeValue.getName());
      assertEquals("testValue", attributeValue.getValue());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      AttributeValue attributeValue = new AttributeValue("attributeName", "attributeValue");

      assertEquals("attributeName", attributeValue.getName());
      assertEquals("attributeValue", attributeValue.getValue());
    }
  }

  @Nested
  class TestNameField {

    @Test
    void shouldSetAndGetName() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setName("newName");

      assertEquals("newName", attributeValue.getName());
    }

    @Test
    void shouldSetNullName() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setName(null);

      assertEquals(null, attributeValue.getName());
    }

    @Test
    void shouldSetEmptyName() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setName("");

      assertEquals("", attributeValue.getName());
    }
  }

  @Nested
  class TestValueField {

    @Test
    void shouldSetAndGetValue() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setValue("newValue");

      assertEquals("newValue", attributeValue.getValue());
    }

    @Test
    void shouldSetNullValue() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setValue(null);

      assertEquals(null, attributeValue.getValue());
    }

    @Test
    void shouldSetEmptyValue() {
      AttributeValue attributeValue = new AttributeValue();

      attributeValue.setValue("");

      assertEquals("", attributeValue.getValue());
    }
  }

  @Nested
  class TestGetType {

    @Test
    void shouldAlwaysReturnStringForGetType() {
      AttributeValue attributeValue = new AttributeValue();

      // getType() should always return "string" regardless of any other state
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeWhenNameIsSet() {
      AttributeValue attributeValue = new AttributeValue();
      attributeValue.setName("testName");

      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeWhenValueIsSet() {
      AttributeValue attributeValue = new AttributeValue();
      attributeValue.setValue("testValue");

      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeWhenBothFieldsAreSet() {
      AttributeValue attributeValue = new AttributeValue();
      attributeValue.setName("name");
      attributeValue.setValue("value");

      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeWhenCreatedWithBuilder() {
      AttributeValue attributeValue = AttributeValue.builder()
          .name("attr")
          .value("val")
          .build();

      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeWhenCreatedWithAllArgsConstructor() {
      AttributeValue attributeValue = new AttributeValue("name", "value");

      assertEquals("string", attributeValue.getType());
    }
  }

  @Nested
  class TestSetType {

    @Test
    void shouldIgnoreSetTypeWithDifferentValue() {
      AttributeValue attributeValue = new AttributeValue();

      // Call setType with a different value
      attributeValue.setType("integer");

      // getType() should still return "string" - the setType is a no-op
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldIgnoreSetTypeWithNull() {
      AttributeValue attributeValue = new AttributeValue();

      // Call setType with null
      attributeValue.setType(null);

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldIgnoreSetTypeWithEmptyString() {
      AttributeValue attributeValue = new AttributeValue();

      // Call setType with empty string
      attributeValue.setType("");

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldIgnoreMultipleSetTypeCalls() {
      AttributeValue attributeValue = new AttributeValue();

      // Call setType multiple times with different values
      attributeValue.setType("boolean");
      attributeValue.setType("number");
      attributeValue.setType("object");
      attributeValue.setType("array");

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldIgnoreSetTypeWithStringValue() {
      AttributeValue attributeValue = new AttributeValue();

      // Call setType with "string" (same as the hardcoded value)
      attributeValue.setType("string");

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldNotAffectOtherFieldsWhenSetTypeCalled() {
      AttributeValue attributeValue = new AttributeValue();
      attributeValue.setName("testName");
      attributeValue.setValue("testValue");

      // Call setType
      attributeValue.setType("integer");

      // Other fields should remain unchanged
      assertEquals("testName", attributeValue.getName());
      assertEquals("testValue", attributeValue.getValue());
      assertEquals("string", attributeValue.getType());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldHaveCorrectEqualsForSameValues() {
      AttributeValue attr1 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();
      AttributeValue attr2 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();

      assertEquals(attr1, attr2);
    }

    @Test
    void shouldHaveCorrectHashCodeForSameValues() {
      AttributeValue attr1 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();
      AttributeValue attr2 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();

      assertEquals(attr1.hashCode(), attr2.hashCode());
    }

    @Test
    void shouldHaveCorrectEqualsAfterSetTypeCalled() {
      AttributeValue attr1 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();
      AttributeValue attr2 = AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();

      // Call setType on one of them
      attr1.setType("integer");

      // They should still be equal since setType is a no-op
      assertEquals(attr1, attr2);
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldHaveCorrectToString() {
      AttributeValue attributeValue = AttributeValue.builder()
          .name("attr")
          .value("val")
          .build();

      String toString = attributeValue.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("attr"));
      assertTrue(toString.contains("val"));
    }

    @Test
    void shouldHaveCorrectToStringForEmptyObject() {
      AttributeValue attributeValue = new AttributeValue();

      String toString = attributeValue.toString();
      assertNotNull(toString);
    }
  }
}

