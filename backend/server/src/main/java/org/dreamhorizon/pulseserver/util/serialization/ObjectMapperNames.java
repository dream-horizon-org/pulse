package org.dreamhorizon.pulseserver.util.serialization;

import com.google.inject.name.Named;

/**
 * {@link Named} qualifier strings for {@link com.fasterxml.jackson.databind.ObjectMapper}
 * bindings.
 */
public final class ObjectMapperNames {

  /** Strict deserialization: unknown JSON properties fail (Jackson default). */
  public static final String NORMAL = "normal";

  /**
   * Lenient deserialization: {@code DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES}
   * is disabled.
   */
  public static final String IGNORE_UNKNOWN_PROPERTIES = "ignoreUnknownProperties";

  private ObjectMapperNames() { }
}
