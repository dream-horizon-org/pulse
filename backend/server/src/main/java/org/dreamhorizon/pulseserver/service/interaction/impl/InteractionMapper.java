package org.dreamhorizon.pulseserver.service.interaction.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class InteractionMapper {
  public static final InteractionMapper INSTANCE = Mappers.getMapper(InteractionMapper.class);

  public InteractionDetails toInteractionDetails(CreateInteractionRequest request) {
    return INSTANCE.toInteractionDetailsBuilder(request)
        .toBuilder()
        .createdBy(request.getUser())
        .createdAt(Timestamp.valueOf(LocalDateTime.now()))
        .updatedBy(request.getUser())
        .status(InteractionStatus.RUNNING)
        .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
        .build();
  }

  public abstract InteractionDetails toInteractionDetailsBuilder(CreateInteractionRequest request);

  public abstract Event copy(Event event);

  public abstract Event.Prop copy(Event.Prop prop);

  public Event.Operator fromString(String operator) {
    return Event.Operator.fromString(operator);
  }
}
