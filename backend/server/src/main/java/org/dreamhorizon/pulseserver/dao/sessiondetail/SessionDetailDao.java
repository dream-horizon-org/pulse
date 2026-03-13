package org.dreamhorizon.pulseserver.dao.sessiondetail;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
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
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionSpanRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionDetailDao {

  private static final int DEFAULT_TIMEOUT_MS = 5000;

  private final ClickhouseQueryService clickhouseQueryService;

  public Single<QueryResultResponse<SessionCoreRow>> getSessionCore(String sessionId) {
    return executeQuery(
        SessionDetailQueries.GET_SESSION_CORE, sessionId, SessionCoreRow.class
    );
  }

  public Single<QueryResultResponse<InteractionRow>> getInteractions(String sessionId) {
    return executeQuery(
        SessionDetailQueries.GET_SESSION_INTERACTIONS, sessionId, InteractionRow.class
    );
  }

  public Single<QueryResultResponse<NetworkRequestRow>> getNetworkRequests(String sessionId) {
    return executeQuery(
        SessionDetailQueries.GET_SESSION_NETWORK, sessionId, NetworkRequestRow.class
    );
  }

  public Single<QueryResultResponse<SessionSpanRow>> getEventSpans(String sessionId) {
    return executeQuery(
        SessionDetailQueries.GET_SESSION_EVENT_SPANS, sessionId, SessionSpanRow.class
    );
  }

  public Single<QueryResultResponse<SessionExceptionRow>> getExceptions(String sessionId) {
    return executeQuery(
        SessionDetailQueries.GET_SESSION_EXCEPTIONS, sessionId, SessionExceptionRow.class
    );
  }

  private <T> Single<QueryResultResponse<T>> executeQuery(
      String queryTemplate, String sessionId, Class<T> clazz
  ) {
    Map<String, Object> params = Map.of("session_id", sessionId);
    String query = new StringSubstitutor(params).replace(queryTemplate);
    String projectId = ProjectContext.requireProjectId();

    QueryConfiguration config = QueryConfiguration
        .newQuery(query)
        .timeoutMs(DEFAULT_TIMEOUT_MS)
        .tenantId(projectId).projectId(projectId)
        .build();

    return clickhouseQueryService.executeQueryOrCreateJob(config, clazz);
  }
}
