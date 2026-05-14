package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

/**
 * Data access for the funnel drop-off attribution panel.
 *
 * <p>Two read paths:
 * <ul>
 *   <li>{@link #queryCauses} — ranked causes per (funnel × step × run), joining the
 *       bridge table against {@code stack_trace_events}, {@code otel_traces}, and
 *       {@code session_summary}.</li>
 *   <li>{@link #queryEvidence} — hydrates one-row-per-session context for the
 *       "View examples" drill-in once the user picks a cause.</li>
 * </ul>
 *
 * <p>{@code mode} selects whether cohorts are sessions (SESSIONS funnels) or users
 * anchored on a canonical session (UNIQUE_USERS funnels). The table swap is pushed
 * into {@link FunnelDropoffQueries} so the DAO stays thin.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelDropoffDao {

  private static final int TIMEOUT_MS = 30_000;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Ranked drop-off causes for the given step of the given funnel run.
   *
   * <p>Two-tier read: first tries the precomputed {@code funnel_dropoff_attribution} table
   * (one indexed lookup, no OTel scans). If that returns zero rows — typically because the
   * funnel hasn't been recomputed since the attribution feature shipped, or no signals
   * fired in the window — falls back to the live join against
   * {@code stack_trace_events} / {@code otel_traces} / {@code session_summary} so older
   * runs and freshly-created funnels still get a populated panel.
   *
   * <p>Returns an empty list when both paths come back empty (no cohort, no signals).
   *
   * @param runTime optional; {@code null} picks the latest run for the funnel.
   * @param mode either {@code UNIQUE_USERS} or {@code SESSIONS} (case-insensitive).
   */
  public Single<List<FunnelDropoffCauseRow>> queryCauses(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    return queryCausesFromAttribution(projectId, funnelId, stepIndex, runTime)
        .flatMap(rows -> rows.isEmpty()
            ? queryCausesLive(projectId, funnelId, stepIndex, runTime, mode)
            : Single.just(rows));
  }

  private Single<List<FunnelDropoffCauseRow>> queryCausesFromAttribution(
      String projectId, long funnelId, int stepIndex, String runTime) {
    String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
        projectId, funnelId, stepIndex, runTime);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, FunnelDropoffCauseRow.class)
        .map(FunnelDropoffDao::causeRowsOrEmpty);
  }

  private Single<List<FunnelDropoffCauseRow>> queryCausesLive(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    String sql = FunnelDropoffQueries.buildCausesSql(projectId, funnelId, stepIndex, runTime, mode);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, FunnelDropoffCauseRow.class)
        .map(FunnelDropoffDao::causeRowsOrEmpty);
  }

  /**
   * Evidence rows for the side-panel's "View examples" drill-in. {@code sessionIds}
   * is the subset picked from {@link FunnelDropoffCauseRow#getExampleSessions()} —
   * typically 5 per cause.
   */
  public Single<List<FunnelDropoffEvidenceRow>> queryEvidence(
      String projectId, long funnelId, int stepIndex, String runTime, String mode,
      List<String> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Single.just(Collections.emptyList());
    }
    String sql = FunnelDropoffQueries.buildEvidenceSql(
        projectId, funnelId, stepIndex, runTime, mode, sessionIds);
    QueryConfiguration config =
        QueryConfiguration.newQuery(sql)
            .timeoutMs(TIMEOUT_MS)
            .tenantId(projectId)
            .projectId(projectId)
            .build();
    return clickhouseQueryService
        .executeQueryOrCreateJob(config, FunnelDropoffEvidenceRow.class)
        .map(FunnelDropoffDao::evidenceRowsOrEmpty);
  }

  private static List<FunnelDropoffCauseRow> causeRowsOrEmpty(
      QueryResultResponse<FunnelDropoffCauseRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }

  private static List<FunnelDropoffEvidenceRow> evidenceRowsOrEmpty(
      QueryResultResponse<FunnelDropoffEvidenceRow> response) {
    if (response == null || response.getRows() == null) {
      return Collections.emptyList();
    }
    return response.getRows();
  }
}
