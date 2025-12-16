package org.dreamhorizon.pulseserver.resources.interaction;

import java.util.List;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionConfig;
import org.dreamhorizon.pulseserver.resources.interaction.models.GetInteractionsRestRequest;
import org.dreamhorizon.pulseserver.resources.interaction.models.GetInteractionsRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.RestInteractionDetail;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.GetInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;


@Mapper
public abstract class RestInteractionMapper {

  public static final RestInteractionMapper INSTANCE = Mappers.getMapper(RestInteractionMapper.class);

  @Mapping(target = "user", source = "userEmail")
  public abstract CreateInteractionRequest toServiceCreateInteractionRequest(RestInteractionDetail request, String userEmail);

  @Mapping(target = "name", source = "actualInteractionName")
  @Mapping(target = "user", source = "userEmail")
  public abstract UpdateInteractionRequest toServiceUpdateInteractionRequest(
      RestInteractionDetail request,
      String actualInteractionName,
      String userEmail);

  public abstract RestInteractionDetail toRestInteractionDetail(InteractionDetails event);

  public abstract List<InteractionConfig> toInteractionConfig(List<InteractionDetails> details);

  public abstract List<Event> toServiceEvents(List<RestInteractionDetail.Event> events);

  public abstract Event toServiceEvent(RestInteractionDetail.Event event);

  public abstract Event.Prop toServiceProd(RestInteractionDetail.Prop prop);

  public abstract GetInteractionsRequest toServiceRequest(GetInteractionsRestRequest request);

  public abstract GetInteractionsRestResponse toRestResponse(GetInteractionsResponse response);

  public InteractionStatus toServiceInteractionStatus(String status) {
    return InteractionStatus.fromString(status);
  }

  public Event.Operator toServicePropOperator(String operator) {
    return Event.Operator.fromString(operator);
  }
}