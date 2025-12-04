package org.dreamhorizon.pulseserver.resources.configs;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public class RestConfigMapper {
  public static final RestConfigMapper INSTANCE = Mappers.getMapper(RestConfigMapper.class);
}
