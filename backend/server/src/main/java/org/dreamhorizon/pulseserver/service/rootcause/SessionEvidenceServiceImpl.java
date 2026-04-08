package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.SessionEvidenceQueryBuilder;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.rootcause.models.EvidenceSession;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionEvidenceServiceImpl implements SessionEvidenceService {

  private final ClickhouseQueryService clickhouseQueryService;

  @Override
  public Single<SessionEvidenceResult> getSessionEvidence(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Map<String, Double> segmentDeltas,
      Integer limit) {

    return Single.fromCallable(
            () ->
                SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
                    projectId,
                    interactionName,
                    startTime,
                    endTime,
                    segmentDimensions,
                    segmentDeltas,
                    limit))
        .doOnSuccess(
            query ->
                log.debug(
                    "Built session evidence query for interaction={}, limit={}, deltas={}",
                    interactionName,
                    limit,
                    segmentDeltas))
        .flatMap(
            sqlQuery -> {
              QueryConfiguration config = QueryConfiguration.newQuery(sqlQuery)
                  .projectId(projectId)
                  .build();
              return clickhouseQueryService.executeQueryOrCreateJob(config);
            })
        .map(this::parseSessionEvidenceResponse)
        .onErrorResumeNext(
            error -> {
              log.error(
                  "Failed to get session evidence for interaction={}: {}",
                  interactionName,
                  error.getMessage(),
                  error);
              return Single.just(SessionEvidenceResult.builder()
                  .sessions(new ArrayList<>())
                  .totalSessionsCount(0)
                  .build());
            });
  }

  @Override
  public Single<SessionEvidenceResult> getSessionEvidence(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Integer limit) {
    return getSessionEvidence(
        projectId,
        interactionName,
        startTime,
        endTime,
        segmentDimensions,
        null,  // No deltas
        limit);
  }

  /**
   * Parse ClickHouse response into SessionEvidenceResult.
   *
   * @param response ClickHouse query response with rows
   * @return SessionEvidenceResult with parsed sessions
   */
  private SessionEvidenceResult parseSessionEvidenceResponse(
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {

    List<EvidenceSession> sessions = new ArrayList<>();

    if (response != null && response.getData() != null) {
      List<GetRawUserEventsResponseDto.Row> rows = response.getData().getRows();
      if (rows != null) {
        for (GetRawUserEventsResponseDto.Row row : rows) {
          try {
            if (row.getRowFields() != null && row.getRowFields().size() >= 1) {
              String sessionId = String.valueOf(row.getRowFields().get(0).getValue());

              EvidenceSession session =
                  EvidenceSession.builder()
                      .sessionId(sessionId)
                      .build();

              sessions.add(session);
            }
          } catch (Exception e) {
            log.warn("Failed to parse evidence session row: {}, error: {}", row, e.getMessage());
          }
        }
      }
    }

    return SessionEvidenceResult.builder()
        .sessions(sessions)
        .totalSessionsCount(sessions.size())
        .build();
  }
}
