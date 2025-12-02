package org.dreamhorizon.pulseserver.dao.interaction;

import org.dreamhorizon.pulseserver.dao.interaction.models.InteractionDetailRow;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class DaoInteractionMapper {
  public static DaoInteractionMapper INSTANCE = Mappers.getMapper(DaoInteractionMapper.class);

  public abstract InteractionDetailRow toInteractionDetailRow(InteractionDetails interactionDetails);

  public abstract Event copy(Event event);

  public abstract Event.Prop copy(Event.Prop prop);
}
