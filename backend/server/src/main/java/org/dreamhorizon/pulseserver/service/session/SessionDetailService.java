package org.dreamhorizon.pulseserver.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.sessiondetail.SessionDetailDao;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.InteractionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.NetworkRequestRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionCoreRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionExceptionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionJourneyRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionSpanRow;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.session.models.EventType;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse.Event;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse.Interaction;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse.NetworkRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse.SessionException;
import org.dreamhorizon.pulseserver.resources.session.models.SessionJourneyResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionDetailService {

  private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
  };

  private final ObjectMapper objectMapper;
  private final SessionDetailDao sessionDetailDao;

  public Single<SessionDetailResponse> getSessionDetail(
      String sessionId, Set<String> includeSections,
      String sessionStart, String sessionEnd, Long durationMs
  ) {
    if (sessionId == null || sessionId.isBlank()) {
      return Single.error(
          ServiceError.INCORRECT_OR_MISSING_PATH_PARAMETERS
              .getCustomException("sessionId is required")
      );
    }

    boolean needEvents = includeSections.contains("events");
    boolean needExceptions = includeSections.contains("exceptions") || needEvents;

    Single<List<SessionCoreRow>> coreSingle = toList(
        sessionDetailDao.getSessionCore(sessionId, sessionStart, sessionEnd));
    Single<List<InteractionRow>> interactionsSingle = toList(
        sessionDetailDao.getInteractions(sessionId, sessionStart, sessionEnd));
    Single<List<NetworkRequestRow>> networkSingle = toList(
        sessionDetailDao.getNetworkRequests(sessionId, sessionStart, sessionEnd));

    Single<List<SessionExceptionRow>> exceptionsSingle = needExceptions
        ? toList(sessionDetailDao.getExceptions(sessionId, sessionStart, sessionEnd))
        : Single.just(Collections.emptyList());

    Single<List<SessionSpanRow>> eventSpansSingle = needEvents
        ? toList(sessionDetailDao.getEventSpans(sessionId, sessionStart, sessionEnd))
        : Single.just(Collections.emptyList());

    return Single.zip(
        coreSingle, interactionsSingle, networkSingle,
        exceptionsSingle, eventSpansSingle,
        (coreRows, interactions, network, exceptions, eventSpans) ->
            assembleResponse(
                sessionId, coreRows, interactions, network, exceptions,
                eventSpans, sessionStart, sessionEnd, durationMs, includeSections
            )
    );
  }

  public Single<SessionJourneyResponse> getSessionJourney(
      String sessionId, String sessionStart, String sessionEnd
  ) {
    if (sessionId == null || sessionId.isBlank()) {
      return Single.error(
          ServiceError.INCORRECT_OR_MISSING_PATH_PARAMETERS
              .getCustomException("sessionId is required")
      );
    }
    return toList(sessionDetailDao.getSessionJourney(sessionId, sessionStart, sessionEnd))
        .map(rows -> {
          String raw = rows.isEmpty() ? null : rows.get(0).getJourney();
          return SessionJourneyResponse.builder()
              .journey(parseJourney(raw))
              .build();
        });
  }

  private SessionDetailResponse assembleResponse(
      String sessionId,
      List<SessionCoreRow> coreRows,
      List<InteractionRow> interactionRows,
      List<NetworkRequestRow> networkRows,
      List<SessionExceptionRow> exceptionRows,
      List<SessionSpanRow> eventSpans,
      String sessionStart,
      String sessionEnd,
      Long durationMs,
      Set<String> includeSections
  ) {
    SessionCoreRow core = firstOrElse(coreRows, defaultCoreRow(sessionId));

    SessionDetailResponse.SessionDetailResponseBuilder builder = SessionDetailResponse.builder()
        .sessionId(core.getSessionId())
        .userId(core.getUserId())
        .isAnonymous(core.getUserId() == null || core.getUserId().isBlank())
        .startTime(sessionStart)
        .endTime(sessionEnd)
        .duration(durationMs != null ? durationMs : 0L)
        .platform(core.getPlatform())
        .device(core.getDevice())
        .osVersion(core.getOsVersion())
        .appVersion(core.getAppVersion())
        .geography(core.getGeography())
        .quality(core.getQualityScore())
        .interactions(mapInteractions(interactionRows))
        .networkRequests(mapNetwork(networkRows));

    if (includeSections.contains("events")) {
      builder.events(mergeTimelines(
          toSpanEvents(eventSpans),
          toNetworkEvents(networkRows),
          toExceptionEvents(exceptionRows)
      ));
    }

    if (includeSections.contains("exceptions")) {
      builder.exceptions(mapExceptions(exceptionRows));
    }

    return builder.build();
  }

  private static <T> T firstOrElse(List<T> list, T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  private static SessionCoreRow defaultCoreRow(String sessionId) {
    return SessionCoreRow.builder()
        .sessionId(sessionId)
        .qualityScore(0)
        .build();
  }

  private List<Interaction> mapInteractions(List<InteractionRow> rows) {
    return rows.stream()
        .map(row -> {
          String status;
          if (row.getFailureCount() > 0 && row.getSuccessCount() == 0) {
            status = "failed";
          } else if (row.getFailureCount() == 0) {
            status = "success";
          } else {
            status = "partial";
          }
          return Interaction.builder()
              .interactionName(row.getInteractionName())
              .successCount(row.getSuccessCount())
              .failureCount(row.getFailureCount())
              .durationMs(row.getAvgDurationMs())
              .status(status)
              .apdexScore(row.getApdexScore())
              .build();
        })
        .collect(Collectors.toList());
  }

  private List<NetworkRequest> mapNetwork(List<NetworkRequestRow> rows) {
    return rows.stream()
        .map(row -> NetworkRequest.builder()
            .timestamp(row.getTimestamp())
            .durationNs(row.getDurationNs())
            .method(row.getHttpMethod())
            .url(row.getHttpUrl())
            .status(row.getHttpStatusCode())
            .target(row.getHttpTarget())
            .traceId(row.getTraceId())
            .spanId(row.getSpanId())
            .build())
        .collect(Collectors.toList());
  }

  private List<SessionException> mapExceptions(List<SessionExceptionRow> rows) {
    return rows.stream()
        .map(row -> SessionException.builder()
            .timestamp(row.getTimestamp())
            .pulseType(row.getPulseType())
            .title(row.getTitle())
            .exceptionStackTrace(row.getExceptionStackTrace())
            .traceId(row.getTraceId())
            .spanId(row.getSpanId())
            .build())
        .collect(Collectors.toList());
  }

  private List<Event> toSpanEvents(List<SessionSpanRow> rows) {
    return rows.stream()
        .map(s -> Event.builder()
            .timestamp(s.getTimestamp())
            .eventType(EventType.fromString(s.getEventType()))
            .description(s.getDescription())
            .durationNs(s.getDurationNs())
            .traceId(s.getTraceId())
            .spanId(s.getSpanId())
            .build())
        .collect(Collectors.toList());
  }

  private List<Event> toNetworkEvents(List<NetworkRequestRow> rows) {
    return rows.stream()
        .map(n -> Event.builder()
            .timestamp(n.getTimestamp())
            .eventType(EventType.API_CALL)
            .description(n.getDescription())
            .durationNs(n.getDurationNs())
            .traceId(n.getTraceId())
            .spanId(n.getSpanId())
            .build())
        .collect(Collectors.toList());
  }

  private List<Event> toExceptionEvents(List<SessionExceptionRow> rows) {
    return rows.stream()
        .map(e -> Event.builder()
            .timestamp(e.getTimestamp())
            .eventType(EventType.fromString(e.getPulseType()))
            .description(e.getTitle())
            .traceId(e.getTraceId())
            .spanId(e.getSpanId())
            .build())
        .collect(Collectors.toList());
  }

  @SafeVarargs
  private List<Event> mergeTimelines(List<Event>... sources) {
    List<Event> result = new ArrayList<>();
    for (List<Event> src : sources) {
      result.addAll(src);
    }
    result.sort(Comparator.comparing(Event::getTimestamp));
    return result;
  }

  private List<String> parseJourney(String journeyJson) {
    if (journeyJson == null || journeyJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(journeyJson, STRING_LIST_TYPE);
    } catch (Exception e) {
      log.warn("Failed to parse journey JSON: {}", journeyJson, e);
      return Collections.emptyList();
    }
  }

  private <T> Single<List<T>> toList(
      Single<? extends org.dreamhorizon.pulseserver.model.QueryResultResponse<T>> single
  ) {
    return single.map(r -> r.getRows() != null ? r.getRows() : Collections.emptyList());
  }
}