package in.horizonos.pulseserver.resources.interaction;

import in.horizonos.pulseserver.resources.interaction.models.GetInteractionsRestRequest;
import in.horizonos.pulseserver.resources.interaction.models.GetInteractionsRestResponse;
import in.horizonos.pulseserver.resources.interaction.models.RestInteractionDetail;
import in.horizonos.pulseserver.service.interaction.models.CreateInteractionRequest;
import in.horizonos.pulseserver.service.interaction.models.Event;
import in.horizonos.pulseserver.service.interaction.models.GetInteractionsRequest;
import in.horizonos.pulseserver.service.interaction.models.GetInteractionsResponse;
import in.horizonos.pulseserver.service.interaction.models.InteractionDetails;
import in.horizonos.pulseserver.service.interaction.models.InteractionStatus;
import in.horizonos.pulseserver.service.interaction.models.UpdateInteractionRequest;
import java.util.List;
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