package org.dreamhorizon.pulseserver.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ObjectMapperUtil {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public String writeValueAsString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T readValue(String content, Class<T> valueType) {
    try {
      return objectMapper.readValue(content, valueType);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T convertValue(Object fromValue, Class<T> toValueType) {
    try {
      return objectMapper.convertValue(fromValue, toValueType);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T convertValue(Object fromValue, JavaType toValueType) {
    try {
      return objectMapper.convertValue(fromValue, toValueType);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public JavaType constructCollectionType(Class<?> collectionClass, Class<?> elementClass) {
    return objectMapper.getTypeFactory().constructCollectionType(
        (Class<? extends Collection<?>>) collectionClass, elementClass);
  }
}
