package org.dreamhorizon.pulseserver.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemplateServiceTest {

  private TemplateService templateService;

  @BeforeEach
  void setUp() {
    templateService = new TemplateService(new ObjectMapper());
  }

  @Nested
  class RenderText {

    @Test
    void shouldReplaceSimplePlaceholder() {
      String template = "Hello {{name}}!";
      Map<String, Object> params = Map.of("name", "World");

      String result = templateService.renderText(template, params);

      assertThat(result).isEqualTo("Hello World!");
    }

    @Test
    void shouldReplaceMultiplePlaceholders() {
      String template = "{{greeting}} {{name}}, welcome to {{product}}!";
      Map<String, Object> params = Map.of(
          "greeting", "Hello",
          "name", "User",
          "product", "Pulse");

      String result = templateService.renderText(template, params);

      assertThat(result).isEqualTo("Hello User, welcome to Pulse!");
    }

    @Test
    void shouldReplaceNestedPlaceholder() {
      String template = "User: {{user.name}}";
      Map<String, Object> params = Map.of(
          "user", Map.of("name", "John"));

      String result = templateService.renderText(template, params);

      assertThat(result).isEqualTo("User: John");
    }

    @Test
    void shouldReturnTemplateWhenParamsNull() {
      String template = "Hello {{name}}!";

      String result = templateService.renderText(template, null);

      assertThat(result).isEqualTo(template);
    }

    @Test
    void shouldReturnTemplateWhenParamsEmpty() {
      String template = "Hello {{name}}!";

      String result = templateService.renderText(template, Map.of());

      assertThat(result).isEqualTo(template);
    }

    @Test
    void shouldReplaceWithEmptyStringWhenValueNull() {
      String template = "Hello {{name}}!";
      Map<String, Object> params = new java.util.HashMap<>();
      params.put("name", null);

      String result = templateService.renderText(template, params);

      assertThat(result).isEqualTo("Hello !");
    }

    @Test
    void shouldReturnNullWhenTemplateNull() {
      String result = templateService.renderText(null, Map.of("key", "value"));

      assertThat(result).isNull();
    }
  }

  @Nested
  class RenderJson {

    @Test
    void shouldReplacePlaceholdersInJsonString() {
      String jsonTemplate = "{\"message\": \"Hello {{name}}!\", \"count\": 42}";
      Map<String, Object> params = Map.of("name", "World");

      String result = templateService.renderJson(jsonTemplate, params);

      assertThat(result).contains("Hello World!");
      assertThat(result).contains("\"count\":42");
    }

    @Test
    void shouldReplaceNestedPlaceholdersInJson() {
      String jsonTemplate = "{\"user\": {\"greeting\": \"{{msg}}\"}}";
      Map<String, Object> params = Map.of("msg", "Hi");

      String result = templateService.renderJson(jsonTemplate, params);

      assertThat(result).contains("\"greeting\":\"Hi\"");
    }

    @Test
    void shouldReturnTemplateWhenParamsNull() {
      String jsonTemplate = "{\"key\": \"{{value}}\"}";

      String result = templateService.renderJson(jsonTemplate, null);

      assertThat(result).isEqualTo(jsonTemplate);
    }

    @Test
    void shouldReturnTemplateWhenParamsEmpty() {
      String jsonTemplate = "{\"key\": \"{{value}}\"}";

      String result = templateService.renderJson(jsonTemplate, Map.of());

      assertThat(result).isEqualTo(jsonTemplate);
    }

    @Test
    void shouldFallbackToTextReplacementOnInvalidJson() {
      String invalidJson = "not valid json {{key}}";
      Map<String, Object> params = Map.of("key", "value");

      String result = templateService.renderJson(invalidJson, params);

      assertThat(result).contains("value");
    }

    @Test
    void shouldReturnNullWhenTemplateNull() {
      String result = templateService.renderJson(null, Map.of("key", "value"));

      assertThat(result).isNull();
    }

    @Test
    void shouldHandleArrayInJson() {
      String jsonTemplate = "{\"items\": [\"{{item1}}\", \"{{item2}}\"]}";
      Map<String, Object> params = Map.of("item1", "a", "item2", "b");

      String result = templateService.renderJson(jsonTemplate, params);

      assertThat(result).contains("\"a\"");
      assertThat(result).contains("\"b\"");
    }
  }
}
