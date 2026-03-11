package org.dreamhorizon.pulseserver.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TemplateService {

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

  private final ObjectMapper objectMapper;

  public String renderText(String template, Map<String, Object> params) {
    if (template == null || params == null || params.isEmpty()) {
      return template;
    }

    StringBuffer result = new StringBuffer();
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

    while (matcher.find()) {
      String placeholder = matcher.group(1).trim();
      Object value = resolveValue(placeholder, params);
      String replacement = value != null ? value.toString() : "";
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }

    matcher.appendTail(result);
    return result.toString();
  }

  public String renderJson(String jsonTemplate, Map<String, Object> params) {
    if (jsonTemplate == null || params == null || params.isEmpty()) {
      return jsonTemplate;
    }

    try {
      JsonNode root = objectMapper.readTree(jsonTemplate);
      JsonNode rendered = renderJsonNode(root, params);
      return objectMapper.writeValueAsString(rendered);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse JSON template, falling back to text replacement", e);
      return renderText(jsonTemplate, params);
    }
  }

  private JsonNode renderJsonNode(JsonNode node, Map<String, Object> params) {
    if (node.isTextual()) {
      String text = node.asText();
      if (PLACEHOLDER_PATTERN.matcher(text).find()) {
        String rendered = renderText(text, params);
        return new TextNode(rendered);
      }
      return node;
    }

    if (node.isObject()) {
      ObjectNode result = objectMapper.createObjectNode();
      node.fields()
          .forEachRemaining(
              entry -> result.set(entry.getKey(), renderJsonNode(entry.getValue(), params)));
      return result;
    }

    if (node.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();
      node.forEach(element -> result.add(renderJsonNode(element, params)));
      return result;
    }

    return node;
  }

  private Object resolveValue(String path, Map<String, Object> params) {
    String[] parts = path.split("\\.");
    Object current = params;

    for (String part : parts) {
      if (current == null) {
        return null;
      }

      if (current instanceof Map) {
        current = ((Map<?, ?>) current).get(part);
      } else {
        return null;
      }
    }

    return current;
  }
}
