package org.dreamhorizon.pulseserver.dao.sessiondetail;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.InteractionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.NetworkRequestRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionCoreRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionExceptionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionJourneyRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionSpanRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionDetailDao {

  private static final int DEFAULT_TIMEOUT_MS = 15000;
  private static final String FALLBACK_START = "2026-01-01 00:00:00.000000000";
  private static final String FALLBACK_END = "2099-12-31 23:59:59.999999999";

  private final ClickhouseQueryService clickhouseQueryService;

  public Single<QueryResultResponse<SessionCoreRow>> getSessionCore(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_CORE, sessionId, sessionStart, sessionEnd, SessionCoreRow.class);
  }

  public Single<QueryResultResponse<InteractionRow>> getInteractions(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_INTERACTIONS, sessionId, sessionStart, sessionEnd, InteractionRow.class);
  }

  public Single<QueryResultResponse<NetworkRequestRow>> getNetworkRequests(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_NETWORK, sessionId, sessionStart, sessionEnd, NetworkRequestRow.class);
  }

  public Single<QueryResultResponse<SessionSpanRow>> getEventSpans(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_EVENT_SPANS, sessionId, sessionStart, sessionEnd, SessionSpanRow.class);
  }

  public Single<QueryResultResponse<SessionExceptionRow>> getExceptions(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_EXCEPTIONS, sessionId, sessionStart, sessionEnd, SessionExceptionRow.class);
  }

  public Single<QueryResultResponse<SessionJourneyRow>> getSessionJourney(
      String sessionId, String sessionStart, String sessionEnd) {
    return executeQuery(SessionDetailQueries.GET_SESSION_JOURNEY, sessionId, sessionStart, sessionEnd, SessionJourneyRow.class);
  }

  private <T> Single<QueryResultResponse<T>> executeQuery(
      String queryTemplate, String sessionId,
      String sessionStart, String sessionEnd, Class<T> clazz) {
    String projectId = ProjectContext.requireProjectId();
    String start = (sessionStart != null && !sessionStart.isBlank()) ? sessionStart : FALLBACK_START;
    String end = (sessionEnd != null && !sessionEnd.isBlank()) ? sessionEnd : FALLBACK_END;

    Map<String, Object> params = new HashMap<>();
    params.put("session_id", sessionId);
    params.put("project_id", escapeChStringLiteral(projectId));
    params.put("session_start", escapeChStringLiteral(start));
    params.put("session_end", escapeChStringLiteral(end));

    String query = new StringSubstitutor(params).replace(queryTemplate);
    QueryConfiguration config = QueryConfiguration
        .newQuery(query)
        .timeoutMs(DEFAULT_TIMEOUT_MS)
        .tenantId(projectId).projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config, clazz);
  }

  private static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}
