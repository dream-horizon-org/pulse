package org.dreamhorizon.pulseserver.resources.configs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;

/**
 * Maps REST {@link PulseConfig} (nested DTOs) to service {@link ConfigData}. Uses Jackson instead of
 * MapStruct so Kotlin config models ({@code SignalsToSampleEntry}, metrics types, etc.) participate
 * without MapStruct APT limitations on Kotlin bytecode.
 */
public final class RestConfigMapper {

  public static final RestConfigMapper INSTANCE = new RestConfigMapper();

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new KotlinModule.Builder().build())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private RestConfigMapper() {}

  public ConfigData toServiceCreateConfigRequest(PulseConfig request, String user) {
    ConfigData data = MAPPER.convertValue(request, ConfigData.class);
    data.setUser(user);
    return data;
  }
}
