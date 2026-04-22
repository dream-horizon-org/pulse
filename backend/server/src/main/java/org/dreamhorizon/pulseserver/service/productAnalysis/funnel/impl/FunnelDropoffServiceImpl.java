package org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.FunnelDropoffDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionStep;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffEvidenceResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelDropoffMapper;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelDropoffService;

/**
 * Default implementation of {@link FunnelDropoffService}.
 *
 * <p>The service validates the funnel exists, that the requested step index is
 * within range, and then delegates to {@link FunnelDropoffDao} passing the
 * funnel's {@code mode} so the DAO picks the right cohort table.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelDropoffServiceImpl implements FunnelDropoffService {

  private final FunnelDefinitionDao funnelDefinitionDao;
  private final FunnelDropoffDao funnelDropoffDao;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Single<FunnelDropoffResponse> getDropoff(
      String projectId, long funnelId, int stepIndex, String runTime) {
    if (stepIndex < 0) {
      return Single.error(ServiceError.FUNNEL_STEP_OUT_OF_RANGE.getException());
    }
    return funnelDefinitionDao
        .findByProjectAndId(projectId, funnelId)
        .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
        .toSingle()
        .flatMap(row -> {
          List<FunnelDefinitionStep> steps = parseSteps(row.getStepsJson());
          if (stepIndex >= steps.size()) {
            return Single.error(ServiceError.FUNNEL_STEP_OUT_OF_RANGE.getException());
          }
          String mode = row.getMode();
          String stepName = stepIndex < steps.size() ? steps.get(stepIndex).getEventName() : null;
          return funnelDropoffDao
              .queryCauses(projectId, funnelId, stepIndex, runTime, mode)
              .map(causes -> {
                long dropoffCohort = causes.isEmpty() ? 0L
                    : causes.get(0).getDropoffCohort() == null ? 0L
                        : causes.get(0).getDropoffCohort();
                long converterCohort = causes.isEmpty() ? 0L
                    : causes.get(0).getConverterCohort() == null ? 0L
                        : causes.get(0).getConverterCohort();
                return FunnelDropoffResponse.builder()
                    .funnelId(funnelId)
                    .stepIndex(stepIndex)
                    .stepName(stepName)
                    .mode(mode)
                    .dropoffCohort(dropoffCohort)
                    .converterCohort(converterCohort)
                    .causes(FunnelDropoffMapper.fromCauseRows(causes))
                    .build();
              });
        });
  }

  @Override
  public Single<FunnelDropoffEvidenceResponse> getEvidence(
      String projectId, long funnelId, int stepIndex, String runTime, List<String> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Single.just(FunnelDropoffEvidenceResponse.builder()
          .examples(Collections.emptyList())
          .build());
    }
    return funnelDefinitionDao
        .findByProjectAndId(projectId, funnelId)
        .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
        .toSingle()
        .flatMap(row -> funnelDropoffDao
            .queryEvidence(projectId, funnelId, stepIndex, runTime, row.getMode(), sessionIds)
            .map(rows -> FunnelDropoffEvidenceResponse.builder()
                .examples(FunnelDropoffMapper.fromEvidenceRows(rows))
                .build()));
  }

  private List<FunnelDefinitionStep> parseSteps(String stepsJson) {
    if (stepsJson == null || stepsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(stepsJson, new TypeReference<List<FunnelDefinitionStep>>() {});
    } catch (Exception e) {
      log.warn("failed to parse funnel steps JSON: {}", e.getMessage());
      return Collections.emptyList();
    }
  }
}
