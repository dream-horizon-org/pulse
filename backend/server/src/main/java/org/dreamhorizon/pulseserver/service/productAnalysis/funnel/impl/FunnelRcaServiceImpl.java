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
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelRcaMapper;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelRcaServiceImpl implements FunnelRcaService {

  private final FunnelDefinitionDao funnelDefinitionDao;
  private final FunnelDropoffDao funnelDropoffDao;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Single<RootCauseResult> getFunnelRootCause(
      String projectId, long funnelId, int focusStepIndex, String runTime) {
    if (focusStepIndex < 0) {
      return Single.error(ServiceError.FUNNEL_STEP_OUT_OF_RANGE.getException());
    }
    return funnelDefinitionDao
        .findByProjectAndId(projectId, funnelId)
        .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
        .toSingle()
        .flatMap(
            row -> {
              List<FunnelDefinitionStep> steps = parseSteps(row.getStepsJson());
              if (focusStepIndex >= steps.size()) {
                return Single.error(ServiceError.FUNNEL_STEP_OUT_OF_RANGE.getException());
              }
              String stepName =
                  focusStepIndex < steps.size() ? steps.get(focusStepIndex).getEventName() : null;
              return funnelDropoffDao
                  .queryCausesFromAttribution(projectId, funnelId, focusStepIndex, runTime)
                  .map(
                      causes -> {
                        if (causes.isEmpty()) {
                          return FunnelRcaMapper.noDataUnavailable(
                              funnelId, focusStepIndex, stepName, row.getMode(), null);
                        }
                        return FunnelRcaMapper.toRootCauseResult(
                            causes, row.getMode(), stepName, focusStepIndex, funnelId);
                      });
            });
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
