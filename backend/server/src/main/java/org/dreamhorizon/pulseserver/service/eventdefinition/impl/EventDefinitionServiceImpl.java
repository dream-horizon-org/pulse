package org.dreamhorizon.pulseserver.service.eventdefinition.impl;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionDao;
import org.dreamhorizon.pulseserver.error.EventDefinitionNotFoundException;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.BulkUploadResult;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.CreateEventDefinitionRequest;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.UpdateEventDefinitionRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EventDefinitionServiceImpl implements EventDefinitionService {

  private static final String[] EXPECTED_HEADERS = {
      "event_name", "event_description", "category",
      "attribute_name", "attribute_description", "attribute_type", "attribute_required"
  };

  private final EventDefinitionDao eventDefinitionDao;

  @Override
  public Single<EventDefinition> createEventDefinition(CreateEventDefinitionRequest request) {
    EventDefinition eventDefinition = EventDefinition.builder()
        .eventName(request.getEventName())
        .displayName(request.getDisplayName())
        .description(request.getDescription())
        .category(request.getCategory())
        .attributes(request.getAttributes())
        .createdBy(request.getUser())
        .build();

    return eventDefinitionDao.createEventDefinition(eventDefinition)
        .flatMap(eventDefinitionDao::getEventDefinitionById)
        .flatMap(this::enrichWithAttributes)
        .onErrorResumeNext(error -> Single.error(toWebException(error)));
  }

  @Override
  public Completable updateEventDefinition(UpdateEventDefinitionRequest request) {
    EventDefinition eventDefinition = EventDefinition.builder()
        .id(request.getId())
        .displayName(request.getDisplayName())
        .description(request.getDescription())
        .category(request.getCategory())
        .attributes(request.getAttributes())
        .updatedBy(request.getUser())
        .build();

    return eventDefinitionDao.updateEventDefinition(eventDefinition)
        .onErrorResumeNext(error -> Completable.error(toWebException(error)));
  }

  @Override
  public Single<EventDefinition> getEventDefinitionById(Long id) {
    return eventDefinitionDao.getEventDefinitionById(id)
        .flatMap(this::enrichWithAttributes)
        .onErrorResumeNext(error -> Single.error(toWebException(error)));
  }

  @Override
  public Single<EventDefinitionPage> queryEventDefinitions(
      String search, String category, int limit, int offset) {
    return eventDefinitionDao.queryEventDefinitions(search, category, limit, offset)
        .flatMap(page -> enrichAllWithAttributes(page.getDefinitions())
            .map(enriched -> EventDefinitionPage.builder()
                .definitions(enriched)
                .totalCount(page.getTotalCount())
                .build()))
        .onErrorResumeNext(error -> Single.error(toWebException(error)));
  }

  @Override
  public Single<List<String>> getDistinctCategories() {
    return eventDefinitionDao.getDistinctCategories()
        .onErrorResumeNext(error -> Single.error(toWebException(error)));
  }

  @Override
  public Completable archiveEventDefinition(Long id, String user) {
    return eventDefinitionDao.archiveEventDefinition(id, user)
        .onErrorResumeNext(error -> Completable.error(toWebException(error)));
  }

  @Override
  public Single<BulkUploadResult> bulkUploadFromCsv(InputStream csvInputStream, String user) {
    return Single.fromCallable(() -> parseCsv(csvInputStream))
        .flatMap(parsedRows -> processUpload(parsedRows, user))
        .onErrorResumeNext(error -> {
          if (error instanceof WebApplicationException) {
            return Single.error(error);
          }
          return Single.error(toWebException(error));
        });
  }

  private Single<EventDefinition> enrichWithAttributes(EventDefinition def) {
    return eventDefinitionDao.getAttributesForEvent(def.getId())
        .map(attrs -> def.toBuilder().attributes(attrs).build());
  }

  private Single<List<EventDefinition>> enrichAllWithAttributes(List<EventDefinition> definitions) {
    if (definitions.isEmpty()) {
      return Single.just(definitions);
    }
    List<Long> ids = definitions.stream()
        .map(EventDefinition::getId)
        .collect(Collectors.toList());

    return eventDefinitionDao.getAttributesForEvents(ids)
        .map(attrMap -> definitions.stream()
            .map(def -> def.toBuilder()
                .attributes(attrMap.getOrDefault(def.getId(), Collections.emptyList()))
                .build())
            .collect(Collectors.toList()));
  }

  private Single<BulkUploadResult> processUpload(
      List<CsvEventRow> parsedRows, String user) {

    List<BulkUploadResult.RowError> errors = new ArrayList<>();
    Map<String, CsvEventGroup> grouped = new LinkedHashMap<>();

    for (CsvEventRow row : parsedRows) {
      if (row.eventName == null || row.eventName.isBlank()) {
        errors.add(BulkUploadResult.RowError.builder()
            .line(row.lineNumber)
            .eventName("")
            .message("Event name cannot be blank")
            .build());
        continue;
      }

      CsvEventGroup group = grouped.computeIfAbsent(row.eventName, k -> {
        CsvEventGroup g = new CsvEventGroup();
        g.eventName = row.eventName;
        g.description = row.description;
        g.category = row.category;
        g.attributes = new ArrayList<>();
        return g;
      });

      if (row.attributeName != null && !row.attributeName.isBlank()) {
        group.attributes.add(EventAttributeDefinition.builder()
            .attributeName(row.attributeName)
            .description(row.attributeDescription)
            .dataType(row.attributeType != null && !row.attributeType.isBlank()
                ? row.attributeType : "string")
            .required("true".equalsIgnoreCase(row.attributeRequired))
            .build());
      }
    }

    if (grouped.isEmpty()) {
      return Single.just(BulkUploadResult.builder()
          .created(0).updated(0).skipped(0).errors(errors).build());
    }

    AtomicInteger created = new AtomicInteger(0);
    AtomicInteger updated = new AtomicInteger(0);

    return Observable.fromIterable(grouped.values())
        .concatMapSingle(group -> {
          EventDefinition def = EventDefinition.builder()
              .eventName(group.eventName)
              .displayName(group.eventName)
              .description(group.description)
              .category(group.category)
              .attributes(group.attributes)
              .createdBy(user)
              .build();

          return eventDefinitionDao.upsertEventDefinition(def)
              .doOnSuccess(id -> {
                if (id > 0) {
                  created.incrementAndGet();
                } else if (id < 0) {
                  updated.incrementAndGet();
                }
              })
              .onErrorResumeNext(error -> {
                errors.add(BulkUploadResult.RowError.builder()
                    .eventName(group.eventName)
                    .message(toUserFriendlyError(error, group.eventName))
                    .build());
                return Single.just(-1L);
              });
        })
        .toList()
        .map(results -> BulkUploadResult.builder()
            .created(created.get())
            .updated(updated.get())
            .skipped(0)
            .errors(errors)
            .build());
  }

  private List<CsvEventRow> parseCsv(InputStream inputStream) {
    List<CsvEventRow> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

      String headerLine = reader.readLine();
      if (headerLine == null) {
        return rows;
      }
      headerLine = stripBom(headerLine);

      validateHeaders(headerLine);

      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        String[] fields = parseCsvLine(line);
        CsvEventRow row = new CsvEventRow();
        row.lineNumber = lineNumber;
        row.eventName = getField(fields, 0);
        row.description = getField(fields, 1);
        row.category = getField(fields, 2);
        row.attributeName = getField(fields, 3);
        row.attributeDescription = getField(fields, 4);
        row.attributeType = getField(fields, 5);
        row.attributeRequired = getField(fields, 6);
        rows.add(row);
      }
    } catch (WebApplicationException e) {
      throw e;
    } catch (Exception e) {
      log.error("CSV parse error: {}", e.getMessage(), e);
      throw ServiceError.INVALID_REQUEST_BODY.getCustomException(
          "The CSV file could not be read. Please check that it is a valid CSV file "
              + "and is not corrupted.",
          e.getMessage(), 400);
    }
    return rows;
  }

  private String stripBom(String line) {
    if (line != null && line.length() > 0 && line.charAt(0) == '\uFEFF') {
      return line.substring(1);
    }
    return line;
  }

  private void validateHeaders(String headerLine) {
    String[] headers = parseCsvLine(headerLine);
    for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
      if (i >= headers.length || !EXPECTED_HEADERS[i].equalsIgnoreCase(headers[i].trim())) {
        throw ServiceError.INVALID_REQUEST_BODY.getCustomException(
            "The CSV file has incorrect column headers. Expected columns: "
                + "event_name, event_description, category, attribute_name, "
                + "attribute_description, attribute_type, attribute_required. "
                + "Please download the template and try again.",
            "Header mismatch at column " + (i + 1), 400);
      }
    }
  }

  private String[] parseCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString().trim());
    return fields.toArray(new String[0]);
  }

  private String getField(String[] fields, int index) {
    if (index < fields.length) {
      String val = fields[index].trim();
      return val.isEmpty() ? null : val;
    }
    return null;
  }

  private static class CsvEventRow {
    int lineNumber;
    String eventName;
    String description;
    String category;
    String attributeName;
    String attributeDescription;
    String attributeType;
    String attributeRequired;
  }

  private static class CsvEventGroup {
    String eventName;
    String description;
    String category;
    List<EventAttributeDefinition> attributes;
  }

  private String toUserFriendlyError(Throwable error, String eventName) {
    String msg = error.getMessage();
    if (msg == null) {
      return "Something went wrong while saving this event. Please try again.";
    }

    String friendly = translateDbError(msg);
    if (friendly != null) {
      return friendly;
    }

    log.warn("Unhandled bulk upload error for event '{}': {}", eventName, msg);
    return "Something went wrong while saving this event. Please try again.";
  }

  private WebApplicationException toWebException(Throwable error) {
    if (error instanceof WebApplicationException) {
      return (WebApplicationException) error;
    }

    String msg = error.getMessage();

    if (error instanceof EventDefinitionNotFoundException) {
      String friendly = "The event definition you're looking for could not be found. "
          + "It may have been archived or deleted.";
      return ServiceError.NOT_FOUND.getCustomException(friendly, friendly, 404);
    }

    if (msg != null) {
      String friendly = translateDbError(msg);
      if (friendly != null) {
        int status = msg.contains("errorCode=1062") || msg.contains("Duplicate entry")
            ? 409 : 400;
        log.warn("Translated DB error: {}", msg);
        return ServiceError.INTERNAL_SERVER_ERROR.getCustomException(friendly, friendly, status);
      }
    }

    log.error("Unexpected event definition error: {}", msg, error);
    String fallback = "Something went wrong. Please try again or contact support "
        + "if the problem persists.";
    return ServiceError.INTERNAL_SERVER_ERROR.getCustomException(fallback, fallback);
  }

  private String translateDbError(String msg) {
    if (msg.contains("errorCode=1062") || msg.contains("Duplicate entry")) {
      if (msg.contains("uk_event_attr")) {
        return "This event has duplicate attributes. "
            + "Each attribute name must be unique within an event.";
      }
      if (msg.contains("uk_project_event")) {
        return "An event with this name already exists. "
            + "Please use a different name or edit the existing event.";
      }
      return "This event conflicts with existing data. "
          + "It may have already been created.";
    }

    if (msg.contains("Data too long") || msg.contains("Data truncation")) {
      return "One or more values are too long. Please shorten event names "
          + "(max 255 chars), descriptions, or attribute values and try again.";
    }

    if (msg.contains("foreign key") || msg.contains("FOREIGN KEY")) {
      return "This operation references data that no longer exists. "
          + "Please refresh the page and try again.";
    }

    if (msg.contains("Connection") || msg.contains("connection")
        || msg.contains("timeout") || msg.contains("Timeout")) {
      return "We're having trouble reaching the database. "
          + "Please try again in a moment.";
    }

    return null;
  }
}
