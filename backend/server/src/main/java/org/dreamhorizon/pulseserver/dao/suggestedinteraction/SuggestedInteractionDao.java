package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.DELETE_PENDING_BY_PROJECT;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.GET_SUGGESTION_BY_ID;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.GET_SUGGESTIONS_BY_PROJECT;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT_AND_STATUS;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.INSERT_SUGGESTION;
import static org.dreamhorizon.pulseserver.dao.suggestedinteraction.Queries.UPDATE_STATUS;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateSuggestedInteractionsRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateSuggestedInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.GetSuggestedInteractionsResponse;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionCreateItem;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.SuggestedInteractionEdge;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SuggestedInteractionDao {

  private final MysqlClient mysqlClient;
  private final ObjectMapperUtil objectMapper;

  private String getProjectId() {
    return ProjectContext.getProjectId();
  }

  /**
   * @param status null/blank/PENDING → pending rows (UI default query);
   *               ALL → all statuses; otherwise filter by that status
   */
  public Single<GetSuggestedInteractionsResponse> getSuggestionsByStatus(String status) {
    String normalized = status == null ? null : status.trim();
    if (normalized == null || normalized.isEmpty() || "PENDING".equalsIgnoreCase(normalized)) {
      return executeSuggestionQuery(GET_SUGGESTIONS_BY_PROJECT, Tuple.of(getProjectId()));
    }
    if ("ALL".equalsIgnoreCase(normalized)) {
      return executeSuggestionQuery(GET_SUGGESTIONS_CATALOG_BY_PROJECT, Tuple.of(getProjectId()));
    }
    return executeSuggestionQuery(
        GET_SUGGESTIONS_CATALOG_BY_PROJECT_AND_STATUS,
        Tuple.of(getProjectId(), normalized.toUpperCase()));
  }

  private Single<GetSuggestedInteractionsResponse> executeSuggestionQuery(String query, Tuple tuple) {
    return mysqlClient.getReaderPool()
        .preparedQuery(query)
        .rxExecute(tuple)
        .map(rows -> {
          List<SuggestedInteractionDetails> suggestions = new ArrayList<>();
          rows.forEach(row -> suggestions.add(mapRow(row)));
          return GetSuggestedInteractionsResponse.builder()
              .suggestions(suggestions)
              .totalSuggestions(suggestions.size())
              .build();
        })
        .doOnError(error -> log.error("Error fetching suggested interactions: ", error));
  }

  public Single<SuggestedInteractionDetails> getSuggestionById(Long id) {
    return mysqlClient.getReaderPool()
        .preparedQuery(GET_SUGGESTION_BY_ID)
        .rxExecute(Tuple.of(id, getProjectId()))
        .map(rows -> {
          for (Row row : rows) {
            return mapRow(row);
          }
          throw ServiceError.NOT_FOUND
              .getCustomException("Suggested interaction not found with id: " + id);
        })
        .doOnError(error -> log.error("Error fetching suggested interaction by id: ", error));
  }

  public Single<CreateSuggestedInteractionsResponse> createSuggestions(
      CreateSuggestedInteractionsRequest request) {
    String projectId = getProjectId();
    List<SuggestedInteractionCreateItem> items = request.getSuggestions();
    if (items.isEmpty()) {
      return Single.just(CreateSuggestedInteractionsResponse.builder()
          .createdCount(0)
          .replacedPendingCount(0)
          .build());
    }

    return mysqlClient.getWriterPool().rxGetConnection()
        .flatMap(conn -> {
          Single<Integer> replaceStep = request.isReplacePending()
              ? conn.preparedQuery(DELETE_PENDING_BY_PROJECT)
                  .rxExecute(Tuple.of(projectId))
                  .map(RowSet::rowCount)
              : Single.just(0);

          return replaceStep.flatMap(replacedCount -> {
            Single<Integer> insertChain = Single.just(0);
            for (SuggestedInteractionCreateItem item : items) {
              insertChain = insertChain.flatMap(createdSoFar ->
                  insertOneSuggestion(conn, projectId, item)
                      .map(ignored -> createdSoFar + 1));
            }
            return insertChain.flatMap(createdCount ->
                conn.rxClose()
                    .andThen(Single.just(CreateSuggestedInteractionsResponse.builder()
                        .createdCount(createdCount)
                        .replacedPendingCount(replacedCount)
                        .build())));
          });
        })
        .doOnError(error -> log.error("Error creating suggested interactions: ", error));
  }

  private Single<RowSet<Row>> insertOneSuggestion(
      io.vertx.rxjava3.sqlclient.SqlConnection conn,
      String projectId,
      SuggestedInteractionCreateItem item) {
    String eventsJson = objectMapper.writeValueAsString(item.getEvents());
    String edgesJson = item.getEdges() == null || item.getEdges().isEmpty()
        ? null
        : objectMapper.writeValueAsString(item.getEdges());
    Tuple tuple = Tuple.from(Arrays.asList(
        projectId,
        eventsJson,
        item.getTotalOccurrences(),
        item.getUniqueSessions(),
        item.getSessionPct(),
        item.getMeanSpanS(),
        item.getMedianSpanS(),
        item.getP95SpanS(),
        item.getCv(),
        edgesJson));
    return conn.preparedQuery(INSERT_SUGGESTION).rxExecute(tuple);
  }

  public Single<EmptyResponse> updateStatus(Long id, String status, String userEmail) {
    Tuple tuple = Tuple.of(status, userEmail, id, getProjectId());
    return mysqlClient.getWriterPool()
        .preparedQuery(UPDATE_STATUS)
        .rxExecute(tuple)
        .flatMap(rows -> {
          if (rows.rowCount() == 0) {
            return Single.error(ServiceError.NOT_FOUND
                .getCustomException("Suggested interaction not found with id: " + id));
          }
          return Single.just(new EmptyResponse());
        })
        .doOnError(error -> log.error("Error updating suggestion status: ", error));
  }

  private SuggestedInteractionDetails mapRow(Row row) {
    List<Event> events = List.of();
    Object eventsRaw = row.getValue("events_json");
    String eventsJson = eventsRaw != null ? eventsRaw.toString() : null;
    if (eventsJson != null) {
      Object parsed = objectMapper.readValue(eventsJson, Object.class);
      events = objectMapper.convertValue(
          parsed,
          objectMapper.constructCollectionType(List.class, Event.class)
      );
    }

    List<SuggestedInteractionEdge> edges = List.of();
    Object edgesRaw = row.getValue("edges_json");
    String edgesJson = edgesRaw != null ? edgesRaw.toString() : null;
    if (edgesJson != null) {
      Object parsed = objectMapper.readValue(edgesJson, Object.class);
      edges = objectMapper.convertValue(
          parsed,
          objectMapper.constructCollectionType(List.class, SuggestedInteractionEdge.class)
      );
    }

    return SuggestedInteractionDetails.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .events(events)
        .totalOccurrences(row.getInteger("total_occurrences"))
        .uniqueSessions(row.getInteger("unique_sessions"))
        .sessionPct(row.getDouble("session_pct"))
        .meanSpanS(row.getDouble("mean_span_s"))
        .medianSpanS(row.getDouble("median_span_s"))
        .p95SpanS(row.getDouble("p95_span_s"))
        .cv(row.getDouble("cv"))
        .edges(edges)
        .status(row.getString("status"))
        .createdAt(Timestamp.valueOf(row.getLocalDateTime("created_at")))
        .build();
  }
}
