package org.dreamhorizon.pulseserver.util.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

/**
 * Central Jackson configuration. Two shared mappers differ only by whether unknown JSON
 * properties fail deserialization ({@link #getNormal()}) or are ignored
 * ({@link #getIgnoringUnknownProperties()}).
 *
 * <p>The unqualified Guice {@link ObjectMapper} binding uses the ignore-unknown mapper for
 * backward compatibility. Inject {@code @Named(ObjectMapperNames.NORMAL)} when you need
 * strict parsing.
 */
public final class ObjectMapperFactory {

  private static final ObjectMapper NORMAL = createNormal();
  private static final ObjectMapper IGNORE_UNKNOWN_PROPERTIES =
      createIgnoringUnknownProperties();

  private ObjectMapperFactory() { }

  private static ObjectMapper createBase() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new KotlinModule.Builder().build());
    objectMapper.configure(
        DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return objectMapper;
  }

  private static ObjectMapper createNormal() {
    ObjectMapper objectMapper = createBase();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    return objectMapper;
  }

  private static ObjectMapper createIgnoringUnknownProperties() {
    ObjectMapper objectMapper = createBase();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper;
  }

  /**
   * Strict mapper: fails on unknown JSON properties (typical Jackson default).
   *
   * @return shared normal {@link ObjectMapper}
   */
  public static ObjectMapper getNormal() {
    return NORMAL;
  }

  /**
   * Lenient mapper: ignores unknown JSON properties during deserialization.
   *
   * @return shared mapper with {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled
   */
  public static ObjectMapper getIgnoringUnknownProperties() {
    return IGNORE_UNKNOWN_PROPERTIES;
  }

  /**
   * Same as {@link #getIgnoringUnknownProperties()}; used by existing call sites and the
   * default Guice binding.
   *
   * @return shared lenient {@link ObjectMapper}
   */
  public static ObjectMapper get() {
    return getIgnoringUnknownProperties();
  }
}
