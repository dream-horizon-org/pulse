package org.dreamhorizon.pulseserver.service.interaction;

import java.util.List;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UploadInteractionMapper {

  UploadInteractionMapper INSTANCE = Mappers.getMapper(UploadInteractionMapper.class);

  List<InteractionConfig> toInteractionConfig(List<InteractionDetails> details);
}

