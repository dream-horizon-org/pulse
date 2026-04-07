package org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatus;
import org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatusResolver;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionListParams;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagEntityType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.FunnelResultsDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.*;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelJourneyTagsListResponse;
import org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchServiceImpl;
import org.dreamhorizon.pulseserver.service.productAnalysis.AnalysisEntityTags;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelResultsMapper;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelServiceImpl implements FunnelService {

  private static final int MAX_PAGE_SIZE = 50;

  private final FunnelDefinitionDao funnelDefinitionDao;
  private final FunnelJourneyTagDao funnelJourneyTagDao;
  private final FunnelResultsDao funnelResultsDao;
  private final AnalyticsBatchServiceImpl analyticsBatchService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Single<Long> create(
    String projectId, CreateFunnelDefinitionRequest request, String createdBy) {
    validateCreateOrUpdate(request.getSteps(), request.getFilters());
    validateRangeBounds(request.getStartTime(), request.getEndTime());

    String stepsJson;
    String filtersJson;
    try {
      stepsJson = objectMapper.writeValueAsString(request.getSteps());
      filtersJson =
        request.getFilters() == null || request.getFilters().isEmpty()
          ? null
          : objectMapper.writeValueAsString(request.getFilters());
    } catch (JsonProcessingException e) {
      return Single.error(ServiceError.INVALID_JSON.getCustomException(e.getMessage()));
    }

    FunnelDefinitionRow row =
      FunnelDefinitionRow.builder()
        .projectId(projectId)
        .name(request.getName().trim())
        .description(StringUtils.trimToNull(request.getDescription()))
        .funnelType(request.getFunnelType().name())
        .stepOrderType(request.getStepOrderType().name())
        .stepsJson(stepsJson)
        .windowSeconds(request.getWindowSeconds())
        .mode(request.getMode().name())
        .filtersJson(filtersJson)
        .dateRangeDays(request.getDateRangeDays())
        .startTime(request.getStartTime())
        .endTime(request.getEndTime())
        .expiry(request.getExpiryDate())
        .createdBy(createdBy)
        .build();

    List<String> tagsToStore = AnalysisEntityTags.normalizeOrThrow(request.getTags());

    return funnelDefinitionDao
      .insert(row)
      .flatMap(
        funnelId ->
          funnelJourneyTagDao
            .replaceTags(
              projectId, FunnelJourneyTagEntityType.FUNNEL, funnelId, tagsToStore)
            .toSingleDefault(funnelId))
      .flatMap(funnelId ->
        analyticsBatchService.triggerFunnelOnSaveJob(funnelId)
          .onErrorReturnItem(false) // Don't fail funnel creation if job submission fails
          .map(triggered -> funnelId)
      )
      .onErrorResumeNext(
        err ->
          Single.error(
            ServiceError.FUNNEL_CREATION_FAILED.getCustomException(
              "Failed to create funnel")));
  }

  @Override
  public Completable update(String projectId, long id, UpdateFunnelDefinitionRequest request) {
    validateCreateOrUpdate(request.getSteps(), request.getFilters());
    validateRangeBounds(request.getStartTime(), request.getEndTime());

    String stepsJson;
    String filtersJson;
    try {
      stepsJson = objectMapper.writeValueAsString(request.getSteps());
      filtersJson =
        request.getFilters() == null || request.getFilters().isEmpty()
          ? null
          : objectMapper.writeValueAsString(request.getFilters());
    } catch (JsonProcessingException e) {
      return Completable.error(ServiceError.INVALID_JSON.getCustomException(e.getMessage()));
    }

    FunnelDefinitionRow row =
      FunnelDefinitionRow.builder()
        .id(id)
        .projectId(projectId)
        .name(request.getName().trim())
        .description(StringUtils.trimToNull(request.getDescription()))
        .funnelType(request.getFunnelType().name())
        .stepOrderType(request.getStepOrderType().name())
        .stepsJson(stepsJson)
        .windowSeconds(request.getWindowSeconds())
        .mode(request.getMode().name())
        .filtersJson(filtersJson)
        .dateRangeDays(request.getDateRangeDays())
        .startTime(request.getStartTime())
        .endTime(request.getEndTime())
        .expiry(request.getExpiry())
        .createdBy(null)
        .createdAt(null)
        .updatedAt(null)
        .latestJobStatus(null)
        .build();

    return funnelDefinitionDao
      .update(id, projectId, row)
      .flatMapCompletable(
        updatedRows -> {
          if (updatedRows == 0) {
            return Completable.error(ServiceError.FUNNEL_NOT_FOUND.getException());
          }
          Completable tagStep = request.getTags() == null
            ? Completable.complete()
            : Completable.defer(() -> {
            List<String> tagsToStore = AnalysisEntityTags.normalizeOrThrow(request.getTags());
            return funnelJourneyTagDao.replaceTags(
              projectId, FunnelJourneyTagEntityType.FUNNEL, id, tagsToStore);
          });
          return tagStep.andThen(
            analyticsBatchService.triggerFunnelOnSaveJob(id)
              .onErrorReturnItem(false)
              .ignoreElement());
        });
  }

  @Override
  public Completable delete(String projectId, long id) {
    return funnelJourneyTagDao
      .deleteAllForEntity(projectId, FunnelJourneyTagEntityType.FUNNEL, id)
      .andThen(
        funnelDefinitionDao
          .delete(projectId, id)
          .flatMapCompletable(
            n -> {
              if (n == 0) {
                return Completable.error(ServiceError.FUNNEL_NOT_FOUND.getException());
              }
              return Completable.complete();
            }));
  }

  @Override
  public Single<FunnelDefinitionResponse> get(String projectId, long id) {
    return funnelDefinitionDao
      .findByProjectAndId(projectId, id)
      .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
      .toSingle()
      .flatMap(
        row -> {
          Single<FunnelResultsResponse> results =
            funnelResultsDao
              .queryLatest(projectId, id)
              .map(FunnelResultsMapper::fromRows)
              .onErrorResumeNext(
                err -> {
                  log.warn(
                    "Failed to load ClickHouse funnel results for funnel {} (project {}): {}",
                    id,
                    projectId,
                    err.toString());
                  return Single.just((FunnelResultsResponse) null);
                });
          Single<List<String>> tags =
            funnelJourneyTagDao
              .listTagsForEntity(projectId, FunnelJourneyTagEntityType.FUNNEL, id)
              .onErrorReturnItem(List.of());
          return Single.zip(results, tags, (r, t) -> toResponse(row, r, t));
        });
  }

  @Override
  public Single<FunnelResultsResponse> getResults(String projectId, long id) {
    return funnelDefinitionDao
      .findByProjectAndId(projectId, id)
      .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
      .toSingle()
      .flatMap(
        ignored ->
          funnelResultsDao
            .queryLatest(projectId, id)
            .map(FunnelResultsMapper::fromRows));
  }

  @Override
  public Single<FunnelDefinitionListResponse> list(String projectId, FunnelListQueryParams query) {
    List<String> statuses = parseComputedStatusParams(query.getStatus());
    FunnelType funnelTypeFilter = parseOptionalFunnelType(query.getFunnelType());
    Instant updatedAfter = parseOptionalInstant(query.getUpdatedAfter());
    Instant updatedBefore = parseOptionalInstant(query.getUpdatedBefore());
    validateUpdatedRange(updatedAfter, updatedBefore);

    String q = StringUtils.trimToNull(query.getSearch());
    boolean wantFts =
      q != null && "fts".equalsIgnoreCase(StringUtils.defaultString(query.getSearchMode(), "fts"));
    String ftsQuery = wantFts ? buildFtsBooleanQuery(q) : null;
    boolean useFts = wantFts && ftsQuery != null && !ftsQuery.isBlank();
    String namePrefix = null;
    if (q != null) {
      if (!useFts) {
        namePrefix = sanitizeLikePrefix(q) + "%";
      }
    }

    int pageSize = Math.min(Math.max(1, query.getPageSize()), MAX_PAGE_SIZE);
    int page = Math.max(1, query.getPage());
    int limit = pageSize;
    int offset = (page - 1) * pageSize;

    FunnelDefinitionListParams params =
      FunnelDefinitionListParams.builder()
        .statuses(statuses)
        .funnelType(funnelTypeFilter == null ? null : funnelTypeFilter.name())
        .nameLikePrefix(namePrefix)
        .ftsBooleanQuery(ftsQuery)
        .useFullTextSearch(useFts)
        .updatedAfter(updatedAfter)
        .updatedBefore(updatedBefore)
        .createdBy(StringUtils.trimToNull(query.getCreatedBy()))
        .limit(limit)
        .offset(offset)
        .build();

    return funnelDefinitionDao
      .listByProject(projectId, params)
      .flatMap(
        funnels -> {
          long totalCount = funnels.isEmpty() ? 0 : funnels.get(0).getTotalCount();
          int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);

          if (funnels.isEmpty()) {
            return Single.just(
              FunnelDefinitionListResponse.builder()
                .items(List.of())
                .totalCount(0)
                .page(page)
                .pageSize(pageSize)
                .totalPages(1)
                .build());
          }
          List<Long> ids = funnels.stream().map(FunnelDefinitionRow::getId).toList();
          return funnelJourneyTagDao
            .listTagsForEntities(projectId, FunnelJourneyTagEntityType.FUNNEL, ids)
            .map(
              tagMap ->
                FunnelDefinitionListResponse.builder()
                  .items(
                    funnels.stream()
                      .map(
                        funnel ->
                          toResponse(
                            funnel, null, tagMap.getOrDefault(funnel.getId(), List.of())))
                      .toList())
                  .totalCount(totalCount)
                  .page(page)
                  .pageSize(pageSize)
                  .totalPages(totalPages)
                  .build());
        });
  }

  @Override
  public Single<FunnelJourneyTagsListResponse> listDistinctTags(String projectId) {
    return funnelJourneyTagDao
      .listDistinctTagsForProject(projectId)
      .map(tags -> FunnelJourneyTagsListResponse.builder().tags(tags).build());
  }

  @Override
  public Completable replaceTags(String projectId, long funnelId, List<String> tags) {
    List<String> normalized = AnalysisEntityTags.normalizeOrThrow(tags);
    return funnelDefinitionDao
      .findByProjectAndId(projectId, funnelId)
      .switchIfEmpty(Maybe.error(ServiceError.FUNNEL_NOT_FOUND.getException()))
      .toSingle()
      .flatMapCompletable(
        ignored ->
          funnelJourneyTagDao.replaceTags(
            projectId, FunnelJourneyTagEntityType.FUNNEL, funnelId, normalized));
  }

  private void validateCreateOrUpdate(
    List<FunnelDefinitionStep> steps, List<FunnelAttributeFilter> globalFilters) {
    if (CollectionUtils.isEmpty(steps)) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("steps must not be empty");
    }
    for (FunnelDefinitionStep step : steps) {
      if (step.getEventName() == null || step.getEventName().isBlank()) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Each step requires eventName");
      }
    }
    validateAttributeFilters(globalFilters);
  }

  private void validateAttributeFilters(List<FunnelAttributeFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return;
    }
    for (FunnelAttributeFilter filter : filters) {
      if (filter.getField() == null || filter.getField().isBlank()) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter attribute is required");
      }
      if (filter.getOperator() == null) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter operator is required");
      }
      if (CollectionUtils.isEmpty(filter.getValue())) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter value must not be empty");
      }
      if (filter.getValue().size() < 1) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Operators require at least one value");
      }
    }
  }

  private void validateRangeBounds(Instant start, Instant end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw ServiceError.INVALID_REQUEST_PARAM.getCustomException("endTime must be >= startTime");
    }
  }

  private void validateUpdatedRange(Instant after, Instant before) {
    if (after != null && before != null && before.isBefore(after)) {
      throw ServiceError.INVALID_REQUEST_PARAM.getCustomException(
        "updatedBefore must be >= updatedAfter");
    }
  }

  private List<String> parseComputedStatusParams(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }

    return raw.stream()
      .filter(Objects::nonNull)
      .flatMap(token -> Arrays.stream(token.split(",")))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .map(AnalysisComputedStatus::fromJson)
      .map(Enum::name)
      .toList();
  }

  private FunnelType parseOptionalFunnelType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return FunnelType.fromJson(raw.trim());
  }

  private Instant parseOptionalInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException e) {
      throw ServiceError.INVALID_REQUEST_PARAM.getCustomException("Invalid time: " + raw);
    }
  }

  private static String buildFtsBooleanQuery(String q) {
    return Arrays.stream(q.trim().split("\\s+"))
      .map(w -> w.replaceAll("[^a-zA-Z0-9_\\-]", ""))
      .filter(s -> !s.isEmpty())
      .map(w -> "+" + w + "*")
      .collect(Collectors.joining(" "));
  }

  private static String sanitizeLikePrefix(String q) {
    return q.trim().replaceAll("[%_\\\\]", "\\\\$0");
  }

  private FunnelDefinitionResponse toResponse(
    FunnelDefinitionRow row,
    FunnelResultsResponse funnelResults,
    List<String> tags) {
    try {
      List<FunnelDefinitionStep> steps =
        objectMapper.readValue(row.getStepsJson(), new TypeReference<>() {
        });
      List<FunnelAttributeFilter> filters = null;
      if (row.getFiltersJson() != null && !row.getFiltersJson().isBlank()) {
        filters = objectMapper.readValue(row.getFiltersJson(), new TypeReference<>() {
        });
      }
      AnalysisComputedStatus computed =
        AnalysisComputedStatusResolver.compute(
          FunnelType.fromJson(row.getFunnelType()), row.getLatestJobStatus());
      return FunnelDefinitionResponse.builder()
        .id(row.getId())
        .projectId(row.getProjectId())
        .name(row.getName())
        .description(row.getDescription())
        .status(computed)
        .funnelType(FunnelType.fromJson(row.getFunnelType()))
        .stepOrderType(StepOrderType.fromJson(row.getStepOrderType()))
        .steps(steps)
        .filters(filters)
        .windowSeconds(row.getWindowSeconds())
        .mode(FunnelMode.valueOf(row.getMode()))
        .dateRangeDays(row.getDateRangeDays())
        .startTime(row.getStartTime())
        .endTime(row.getEndTime())
        .expiry(row.getExpiry())
        .createdAt(row.getCreatedAt())
        .updatedAt(row.getUpdatedAt())
        .createdBy(row.getCreatedBy())
        .funnelResults(funnelResults)
        .tags(tags)
        .build();
    } catch (JsonProcessingException e) {
      log.error("Corrupt funnel JSON for id {}", row.getId(), e);
      throw ServiceError.INTERNAL_SERVER_ERROR.getCustomException("Stored funnel definition is invalid");
    }
  }
}
