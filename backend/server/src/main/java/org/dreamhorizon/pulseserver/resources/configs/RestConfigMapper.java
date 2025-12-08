package org.dreamhorizon.pulseserver.resources.configs;

import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class RestConfigMapper {
  public static final RestConfigMapper INSTANCE = Mappers.getMapper(RestConfigMapper.class);

  public abstract ConfigData toServiceCreateConfigRequest(PulseConfig request, String user);
}
