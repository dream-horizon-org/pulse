package org.dreamhorizon.pulseserver.resources.eventdefinition;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse.EventMetadata;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse.EventProperty;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse.EventSearchItem;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestBulkUploadResponse;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventAttribute;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinition;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.RestEventDefinitionListResponse;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.BulkUploadResult;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.CreateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.UpdateEventDefinitionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class EventDefinitionMapper {

  public static final EventDefinitionMapper INSTANCE =
      Mappers.getMapper(EventDefinitionMapper.class);

  @Mapping(target = "isArchived", source = "archived")
  public abstract RestEventDefinition toRestModel(EventDefinition def);

  @Mapping(target = "isRequired", source = "required")
  @Mapping(target = "isArchived", source = "archived")
  public abstract RestEventAttribute toRestAttribute(EventAttributeDefinition attr);

  public List<RestEventAttribute> toRestAttributes(List<EventAttributeDefinition> attrs) {
    if (attrs == null) {
      return Collections.emptyList();
    }
    return attrs.stream().map(this::toRestAttribute).collect(Collectors.toList());
  }

  public RestEventDefinitionListResponse toRestListResponse(EventDefinitionPage page) {
    return RestEventDefinitionListResponse.builder()
        .eventDefinitions(page.getDefinitions().stream()
            .map(this::toRestModel)
            .collect(Collectors.toList()))
        .totalCount(page.getTotalCount())
        .build();
  }

  @Mapping(target = "user", source = "userEmail")
  @Mapping(target = "eventName", source = "restRequest.eventName")
  @Mapping(target = "displayName", source = "restRequest.displayName")
  @Mapping(target = "description", source = "restRequest.description")
  @Mapping(target = "category", source = "restRequest.category")
  @Mapping(target = "attributes", source = "restRequest.attributes")
  public abstract CreateEventDefinitionRequest toCreateRequest(
      RestEventDefinition restRequest, String userEmail);

  @Mapping(target = "user", source = "userEmail")
  @Mapping(target = "id", source = "id")
  @Mapping(target = "displayName", source = "restRequest.displayName")
  @Mapping(target = "description", source = "restRequest.description")
  @Mapping(target = "category", source = "restRequest.category")
  @Mapping(target = "attributes", source = "restRequest.attributes")
  public abstract UpdateEventDefinitionRequest toUpdateRequest(
      RestEventDefinition restRequest, Long id, String userEmail);

  @Mapping(target = "required", source = "isRequired", defaultValue = "false")
  @Mapping(target = "archived", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "eventDefinitionId", ignore = true)
  public abstract EventAttributeDefinition toServiceAttribute(RestEventAttribute attr);

  public abstract List<EventAttributeDefinition> toServiceAttributes(
      List<RestEventAttribute> attrs);

  public RestBulkUploadResponse toRestBulkUploadResponse(BulkUploadResult result) {
    return RestBulkUploadResponse.builder()
        .created(result.getCreated())
        .updated(result.getUpdated())
        .skipped(result.getSkipped())
        .errors(toRestRowErrors(result.getErrors()))
        .build();
  }

  public abstract RestBulkUploadResponse.RowError toRestRowError(BulkUploadResult.RowError error);

  public List<RestBulkUploadResponse.RowError> toRestRowErrors(
      List<BulkUploadResult.RowError> errors) {
    if (errors == null) {
      return Collections.emptyList();
    }
    return errors.stream().map(this::toRestRowError).collect(Collectors.toList());
  }

  public EventSearchResponse toEventSearchResponse(EventDefinitionPage page) {
    return EventSearchResponse.builder()
        .eventList(page.getDefinitions().stream()
            .map(this::toSearchItem)
            .collect(Collectors.toList()))
        .recordCount((int) page.getTotalCount())
        .build();
  }

  EventSearchItem toSearchItem(EventDefinition def) {
    return EventSearchItem.builder()
        .metadata(EventMetadata.builder()
            .eventName(def.getEventName())
            .description(def.getDescription() != null ? def.getDescription() : "")
            .screenNames(Collections.emptyList())
            .archived(def.isArchived())
            .isActive(!def.isArchived())
            .build())
        .properties(def.getAttributes() == null ? Collections.emptyList()
            : def.getAttributes().stream()
            .map(attr -> EventProperty.builder()
                .propertyName(attr.getAttributeName())
                .description(attr.getDescription() != null ? attr.getDescription() : "")
                .archived(attr.isArchived())
                .build())
            .collect(Collectors.toList()))
        .build();
  }
}
