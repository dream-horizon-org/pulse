package org.dreamhorizon.pulseserver.dao.eventdefinition;

import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.ARCHIVE_EVENT_DEFINITION;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.DELETE_ATTRIBUTES_FOR_EVENT;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.GET_ATTRIBUTES_FOR_EVENT;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.GET_DISTINCT_CATEGORIES;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.GET_EVENT_DEFINITION_BY_ID;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.GET_EVENT_DEFINITION_ID_BY_NAME;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.INSERT_ATTRIBUTE;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.INSERT_EVENT_DEFINITION;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.UPDATE_EVENT_DEFINITION;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.UPSERT_ATTRIBUTE;
import static org.dreamhorizon.pulseserver.dao.eventdefinition.EventDefinitionQueries.UPSERT_EVENT_DEFINITION;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.error.EventDefinitionNotFoundException;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class EventDefinitionDao {

  private final MysqlClient mysqlClient;

  private String getProjectId() {
    return TenantContext.requireTenantId();
  }

  public Single<EventDefinitionPage> queryEventDefinitions(
      String search, String category, int limit, int offset) {
    String projectId = getProjectId();
    boolean hasSearch = search != null && !search.isBlank();
    boolean hasCategory = category != null && !category.isBlank();

    String countQuery = EventDefinitionQueries.buildCountQuery(hasSearch, hasCategory);
    String listQuery = EventDefinitionQueries.buildListQuery(hasSearch, hasCategory);

    Tuple countParams = Tuple.tuple().addString(projectId);
    Tuple listParams = Tuple.tuple().addString(projectId);
    if (hasSearch) {
      countParams.addString(search).addString(search).addString(search);
      listParams.addString(search).addString(search).addString(search);
    }
    if (hasCategory) {
      countParams.addString(category);
      listParams.addString(category);
    }
    listParams.addInteger(limit).addInteger(offset);

    Single<Long> countSingle = mysqlClient.getReaderPool()
        .preparedQuery(countQuery)
        .rxExecute(countParams)
        .map(this::extractCount);

    Single<List<EventDefinition>> listSingle = mysqlClient.getReaderPool()
        .preparedQuery(listQuery)
        .rxExecute(listParams)
        .map(this::mapRowsToEventDefinitions);

    return Single.zip(countSingle, listSingle,
            (totalCount, defs) -> EventDefinitionPage.builder()
                .definitions(defs).totalCount(totalCount).build())
        .doOnError(error -> log.error("Error querying event definitions: ", error));
  }

  private Long extractCount(RowSet<Row> rows) {
    if (rows.iterator().hasNext()) {
      Row row = rows.iterator().next();
      Object val = row.getValue(0);
      log.debug("Count query result: value={}, type={}", val,
          val != null ? val.getClass().getSimpleName() : "null");
      if (val instanceof Number) {
        return ((Number) val).longValue();
      }
    }
    return 0L;
  }

  public Single<List<String>> getDistinctCategories() {
    String projectId = getProjectId();
    return mysqlClient.getReaderPool()
        .preparedQuery(GET_DISTINCT_CATEGORIES)
        .rxExecute(Tuple.of(projectId))
        .map(rows -> {
          List<String> categories = new ArrayList<>();
          rows.forEach(row -> categories.add(row.getString("category")));
          return categories;
        })
        .doOnError(error -> log.error("Error fetching categories: ", error));
  }

  public Single<EventDefinition> getEventDefinitionById(Long id) {
    return mysqlClient.getReaderPool()
        .preparedQuery(GET_EVENT_DEFINITION_BY_ID)
        .rxExecute(Tuple.of(id, getProjectId()))
        .flatMap(rows -> {
          if (!rows.iterator().hasNext()) {
            return Single.error(
                new EventDefinitionNotFoundException(id));
          }
          return Single.just(mapRowToEventDefinition(rows.iterator().next()));
        })
        .doOnError(error -> log.error("Error fetching event definition {}: ", id, error));
  }

  public Single<Long> createEventDefinition(EventDefinition eventDefinition) {
    Tuple params = Tuple.tuple()
        .addString(getProjectId())
        .addString(eventDefinition.getEventName())
        .addString(eventDefinition.getDisplayName())
        .addString(eventDefinition.getDescription())
        .addString(eventDefinition.getCategory())
        .addString(eventDefinition.getCreatedBy())
        .addString(eventDefinition.getCreatedBy());

    return mysqlClient.getWriterPool()
        .rxGetConnection()
        .<Long>flatMap(conn -> conn.preparedQuery(INSERT_EVENT_DEFINITION)
            .rxExecute(params)
            .<Long>flatMap(rows -> {
              Long insertedId = Long.parseLong(
                  rows.property(MySQLClient.LAST_INSERTED_ID).toString());
              if (eventDefinition.getAttributes() != null
                  && !eventDefinition.getAttributes().isEmpty()) {
                return insertAttributes(conn, insertedId, eventDefinition.getAttributes())
                    .andThen(Single.just(insertedId));
              }
              return Single.just(insertedId);
            })
            .doFinally(conn::close))
        .doOnError(error -> log.error("Error creating event definition: ", error));
  }

  /**
   * Returns positive ID if a new row was created, negative ID if an existing row was updated.
   */
  public Single<Long> upsertEventDefinition(EventDefinition eventDefinition) {
    Tuple params = Tuple.tuple()
        .addString(getProjectId())
        .addString(eventDefinition.getEventName())
        .addString(eventDefinition.getDisplayName())
        .addString(eventDefinition.getDescription())
        .addString(eventDefinition.getCategory())
        .addString(eventDefinition.getCreatedBy())
        .addString(eventDefinition.getCreatedBy());

    return mysqlClient.getWriterPool()
        .rxGetConnection()
        .<Long>flatMap(conn -> conn.preparedQuery(UPSERT_EVENT_DEFINITION)
            .rxExecute(params)
            .<Long>flatMap(rows -> {
              boolean wasInserted = rows.rowCount() == 1;
              return getEventDefinitionIdByName(conn, eventDefinition.getEventName())
                  .flatMap(eventId -> upsertAttributes(
                      conn, eventId, eventDefinition.getAttributes())
                      .andThen(Single.just(wasInserted ? eventId : -eventId)));
            })
            .doFinally(conn::close))
        .doOnError(error -> log.error("Error upserting event definition: ", error));
  }

  public Completable updateEventDefinition(EventDefinition eventDefinition) {
    return mysqlClient.getWriterPool()
        .rxGetConnection()
        .flatMapCompletable(conn -> conn.preparedQuery(UPDATE_EVENT_DEFINITION)
            .rxExecute(Tuple.of(
                eventDefinition.getDisplayName(),
                eventDefinition.getDescription(),
                eventDefinition.getCategory(),
                eventDefinition.getUpdatedBy(),
                eventDefinition.getId(),
                getProjectId()
            ))
            .flatMapCompletable(rows -> {
              if (eventDefinition.getAttributes() != null) {
                return replaceAttributes(
                    conn, eventDefinition.getId(), eventDefinition.getAttributes());
              }
              return Completable.complete();
            })
            .doFinally(conn::close))
        .doOnError(error -> log.error("Error updating event definition: ", error));
  }

  public Completable archiveEventDefinition(Long id, String user) {
    return mysqlClient.getWriterPool()
        .preparedQuery(ARCHIVE_EVENT_DEFINITION)
        .rxExecute(Tuple.of(user, id, getProjectId()))
        .ignoreElement()
        .doOnError(error -> log.error("Error archiving event definition {}: ", id, error));
  }

  public Single<List<EventAttributeDefinition>> getAttributesForEvent(Long eventDefinitionId) {
    return mysqlClient.getReaderPool()
        .preparedQuery(GET_ATTRIBUTES_FOR_EVENT)
        .rxExecute(Tuple.of(eventDefinitionId))
        .map(rows -> {
          List<EventAttributeDefinition> attributes = new ArrayList<>();
          rows.forEach(row -> attributes.add(mapRowToAttribute(row)));
          return attributes;
        })
        .doOnError(error -> log.error(
            "Error fetching attributes for event {}: ", eventDefinitionId, error));
  }

  public Single<Map<Long, List<EventAttributeDefinition>>> getAttributesForEvents(
      List<Long> eventDefinitionIds) {
    if (eventDefinitionIds.isEmpty()) {
      return Single.just(new HashMap<>());
    }
    String query = EventDefinitionQueries.getAttributesForEventsBatch(eventDefinitionIds.size());
    Tuple params = Tuple.tuple();
    eventDefinitionIds.forEach(params::addLong);

    return mysqlClient.getReaderPool()
        .preparedQuery(query)
        .rxExecute(params)
        .map(rows -> {
          Map<Long, List<EventAttributeDefinition>> grouped = new HashMap<>();
          rows.forEach(row -> {
            EventAttributeDefinition attr = mapRowToAttribute(row);
            grouped.computeIfAbsent(attr.getEventDefinitionId(), k -> new ArrayList<>())
                .add(attr);
          });
          return grouped;
        })
        .doOnError(error -> log.error("Error fetching batch attributes: ", error));
  }

  private Completable insertAttributes(
      SqlConnection conn, Long eventDefinitionId,
      List<EventAttributeDefinition> attributes) {
    return Observable.fromIterable(attributes)
        .flatMapCompletable(attr -> conn.preparedQuery(INSERT_ATTRIBUTE)
            .rxExecute(Tuple.of(
                eventDefinitionId,
                attr.getAttributeName(),
                attr.getDescription(),
                attr.getDataType() != null ? attr.getDataType() : "string",
                attr.isRequired()
            ))
            .ignoreElement());
  }

  private Completable upsertAttributes(
      SqlConnection conn, Long eventDefinitionId,
      List<EventAttributeDefinition> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Completable.complete();
    }
    return Observable.fromIterable(attributes)
        .flatMapCompletable(attr -> conn.preparedQuery(UPSERT_ATTRIBUTE)
            .rxExecute(Tuple.of(
                eventDefinitionId,
                attr.getAttributeName(),
                attr.getDescription(),
                attr.getDataType() != null ? attr.getDataType() : "string",
                attr.isRequired()
            ))
            .ignoreElement());
  }

  private Completable replaceAttributes(
      SqlConnection conn, Long eventDefinitionId,
      List<EventAttributeDefinition> attributes) {
    return conn.begin()
        .flatMapCompletable(tx -> conn.preparedQuery(DELETE_ATTRIBUTES_FOR_EVENT)
            .rxExecute(Tuple.of(eventDefinitionId))
            .ignoreElement()
            .andThen(insertAttributes(conn, eventDefinitionId, attributes))
            .andThen(tx.rxCommit())
            .onErrorResumeNext(err -> tx.rxRollback().andThen(Completable.error(err))));
  }

  private Single<Long> getEventDefinitionIdByName(SqlConnection conn, String eventName) {
    return conn.preparedQuery(GET_EVENT_DEFINITION_ID_BY_NAME)
        .rxExecute(Tuple.of(getProjectId(), eventName))
        .flatMap(rows -> {
          if (!rows.iterator().hasNext()) {
            return Single.error(
                new EventDefinitionNotFoundException(eventName));
          }
          return Single.just(rows.iterator().next().getLong("id"));
        });
  }

  private List<EventDefinition> mapRowsToEventDefinitions(RowSet<Row> rows) {
    List<EventDefinition> definitions = new ArrayList<>();
    rows.forEach(row -> definitions.add(mapRowToEventDefinition(row)));
    return definitions;
  }


  private EventDefinition mapRowToEventDefinition(Row row) {
    return EventDefinition.builder()
        .id(row.getLong("id"))
        .eventName(row.getString("event_name"))
        .displayName(row.getString("display_name"))
        .description(row.getString("description"))
        .category(row.getString("category"))
        .archived(row.getBoolean("is_archived"))
        .createdBy(row.getString("created_by"))
        .updatedBy(row.getString("updated_by"))
        .createdAt(Timestamp.valueOf(row.getLocalDateTime("created_at")))
        .updatedAt(Timestamp.valueOf(row.getLocalDateTime("updated_at")))
        .build();
  }

  private EventAttributeDefinition mapRowToAttribute(Row row) {
    return EventAttributeDefinition.builder()
        .id(row.getLong("id"))
        .eventDefinitionId(row.getLong("event_definition_id"))
        .attributeName(row.getString("attribute_name"))
        .description(row.getString("description"))
        .dataType(row.getString("data_type"))
        .required(row.getBoolean("is_required"))
        .archived(row.getBoolean("is_archived"))
        .build();
  }
}
