package org.dreamhorizon.pulseserver.service.productAnalysis.journey.impl;

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
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagEntityType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyListParams;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.JourneyResultsDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.*;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.ListFilterOptions;
import org.dreamhorizon.pulseserver.service.productAnalysis.AnalysisEntityTags;
import org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchServiceImpl;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.JourneyResultsMapper;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.JourneyService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JourneyServiceImpl implements JourneyService {

  private static final int MAX_PAGE_SIZE = 50;

  private final JourneyDao journeyDao;
  private final FunnelJourneyTagDao funnelJourneyTagDao;
  private final JourneyResultsDao journeyResultsDao;
  private final AnalyticsBatchServiceImpl analyticsBatchService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Single<Long> create(String projectId, CreateJourneyRequest request, String createdBy) {
    validateFilters(request.getFilters());
    validateTimeBounds(request.getStartTime(), request.getEndTime());

    String filtersJson;
    try {
      filtersJson =
        request.getFilters() == null || request.getFilters().isEmpty()
          ? null
          : objectMapper.writeValueAsString(request.getFilters());
    } catch (JsonProcessingException e) {
      return Single.error(ServiceError.INVALID_JSON.getCustomException(e.getMessage()));
    }

    JourneyRow row =
      JourneyRow.builder()
        .id(0L)
        .projectId(projectId)
        .name(request.getName().trim())
        .description(StringUtils.trimToNull(request.getDescription()))
        .anchorEvent(request.getAnchorEvent().trim())
        .direction(request.getDirection().name())
        .depth(request.getDepth())
        .mode(request.getMode().name())
        .filtersJson(filtersJson)
        .startTime(request.getStartTime())
        .endTime(request.getEndTime())
        .journeyType(request.getJourneyType().name())
        .expiry(request.getExpiry())
        .dateRangeDays(request.getDateRangeDays())
        .createdBy(createdBy)
        .createdAt(null)
        .updatedAt(null)
        .latestJobStatus(null)
        .build();

    List<String> tagsToStore = AnalysisEntityTags.normalizeOrThrow(request.getTags());

    return journeyDao
      .insert(row)
      .flatMap(
        journeyId ->
          funnelJourneyTagDao
            .replaceTags(
              projectId, FunnelJourneyTagEntityType.JOURNEY, journeyId, tagsToStore)
            .toSingleDefault(journeyId))
      .flatMap(journeyId ->
        analyticsBatchService.triggerJourneyOnSaveJob(journeyId)
          .onErrorReturnItem(false) // Don't fail journey creation if job submission fails
          .map(triggered -> journeyId)
      )
      .onErrorResumeNext(
        err ->
          Single.error(
            ServiceError.JOURNEY_CREATION_FAILED.getCustomException(
              "Failed to create journey")));
  }

  @Override
  public Completable update(String projectId, long id, UpdateJourneyRequest request) {
    validateFilters(request.getFilters());
    validateTimeBounds(request.getStartTime(), request.getEndTime());

    String filtersJson;
    try {
      filtersJson =
        request.getFilters() == null || request.getFilters().isEmpty()
          ? null
          : objectMapper.writeValueAsString(request.getFilters());
    } catch (JsonProcessingException e) {
      return Completable.error(ServiceError.INVALID_JSON.getCustomException(e.getMessage()));
    }

    JourneyRow row =
      JourneyRow.builder()
        .id(id)
        .projectId(projectId)
        .name(request.getName().trim())
        .description(StringUtils.trimToNull(request.getDescription()))
        .anchorEvent(request.getAnchorEvent().trim())
        .direction(request.getDirection().name())
        .depth(request.getDepth())
        .mode(request.getMode().name())
        .filtersJson(filtersJson)
        .startTime(request.getStartTime())
        .endTime(request.getEndTime())
        .journeyType(request.getJourneyType().name())
        .expiry(request.getExpiry())
        .dateRangeDays(request.getDateRangeDays())
        .createdBy(null)
        .createdAt(null)
        .updatedAt(null)
        .latestJobStatus(null)
        .build();

    return journeyDao
      .update(id, projectId, row)
      .flatMapCompletable(
        n -> {
          if (n == 0) {
            return Completable.error(ServiceError.JOURNEY_NOT_FOUND.getException());
          }
          Completable tagStep = request.getTags() == null
            ? Completable.complete()
            : Completable.defer(() -> {
            List<String> tagsToStore = AnalysisEntityTags.normalizeOrThrow(request.getTags());
            return funnelJourneyTagDao.replaceTags(
              projectId, FunnelJourneyTagEntityType.JOURNEY, id, tagsToStore);
          });
          return tagStep.andThen(
            analyticsBatchService.triggerJourneyOnSaveJob(id)
              .onErrorReturnItem(false)
              .ignoreElement());
        });
  }

  @Override
  public Completable delete(String projectId, long id) {
    return funnelJourneyTagDao
      .deleteAllForEntity(projectId, FunnelJourneyTagEntityType.JOURNEY, id)
      .andThen(
        journeyDao
          .delete(projectId, id)
          .flatMapCompletable(
            n -> {
              if (n == 0) {
                return Completable.error(ServiceError.JOURNEY_NOT_FOUND.getException());
              }
              return Completable.complete();
            }));
  }

  @Override
  public Single<JourneyResponse> get(String projectId, long id) {
    return journeyDao
      .findByProjectAndId(projectId, id)
      .switchIfEmpty(Maybe.error(ServiceError.JOURNEY_NOT_FOUND.getException()))
      .toSingle()
      .flatMap(
        row -> {
          Single<JourneyResultsResponse> graph =
            journeyResultsDao
              .queryLatest(projectId, id, row.getDirection())
              .map(JourneyResultsMapper::fromRows)
              .onErrorResumeNext(
                err -> {
                  log.warn(
                    "Failed to load ClickHouse journey results for journey {} (project {}):"
                      + " {}",
                    id,
                    projectId,
                    err.toString());
                  return Single.just((JourneyResultsResponse) null);
                });
          Single<List<String>> tags =
            funnelJourneyTagDao
              .listTagsForEntity(projectId, FunnelJourneyTagEntityType.JOURNEY, id)
              .onErrorReturnItem(List.of());
          return Single.zip(graph, tags, (g, t) -> toResponse(row, g, t));
        });
  }

  @Override
  public Single<JourneyListResponse> list(String projectId, JourneyListQueryParams query) {
    List<String> statuses = parseComputedStatusParams(query.getStatus());
    FunnelType journeyTypeFilter = parseOptionalJourneyType(query.getJourneyType());
    Instant updatedAfter = parseOptionalInstant(query.getUpdatedAfter());
    Instant updatedBefore = parseOptionalInstant(query.getUpdatedBefore());
    validateUpdatedRange(updatedAfter, updatedBefore);

    String q = StringUtils.trimToNull(query.getSearch());
    boolean wantFts =
      q != null && "fts".equalsIgnoreCase(StringUtils.defaultString(query.getSearchMode(), "fts"));
    String ftsQuery = wantFts ? buildFtsBooleanQuery(q) : null;
    boolean useFts = wantFts && ftsQuery != null && !ftsQuery.isBlank();
    String namePrefix = null;
    if (q != null && !useFts) {
      namePrefix = sanitizeLikePrefix(q) + "%";
    }

    int pageSize = Math.min(Math.max(1, query.getPageSize()), MAX_PAGE_SIZE);
    int page = Math.max(1, query.getPage());
    int limit = pageSize;
    int offset = (page - 1) * pageSize;

    JourneyListParams params =
      JourneyListParams.builder()
        .statuses(statuses)
        .journeyType(journeyTypeFilter == null ? null : journeyTypeFilter.name())
        .nameLikePrefix(namePrefix)
        .ftsBooleanQuery(ftsQuery)
        .useFullTextSearch(useFts)
        .updatedAfter(updatedAfter)
        .updatedBefore(updatedBefore)
        .createdBy(StringUtils.trimToNull(query.getCreatedBy()))
        .limit(limit)
        .offset(offset)
        .build();

    return Single.zip(
      journeyDao.listByProject(projectId, params),
      journeyDao.listDistinctCreatedBy(projectId),
      funnelJourneyTagDao.listDistinctTagsForProject(projectId),
      (rows, creators, allTags) -> new Object[] {rows, creators, allTags})
      .flatMap(
        arr -> {
          @SuppressWarnings("unchecked")
          List<JourneyRow> rows = (List<JourneyRow>) arr[0];
          @SuppressWarnings("unchecked")
          List<String> creators = (List<String>) arr[1];
          @SuppressWarnings("unchecked")
          List<String> allTags = (List<String>) arr[2];

          long totalCount = rows.isEmpty() ? 0 : rows.get(0).getTotalCount();
          int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);

          ListFilterOptions filterOptions = ListFilterOptions.builder()
            .creators(creators)
            .tags(allTags)
            .build();

          if (rows.isEmpty()) {
            return Single.just(
              JourneyListResponse.builder()
                .items(List.of())
                .totalCount(0)
                .page(page)
                .pageSize(pageSize)
                .totalPages(1)
                .filterOptions(filterOptions)
                .build());
          }
          List<Long> ids = rows.stream().map(JourneyRow::getId).toList();
          return funnelJourneyTagDao
            .listTagsForEntities(projectId, FunnelJourneyTagEntityType.JOURNEY, ids)
            .map(
              tagMap ->
                JourneyListResponse.builder()
                  .items(
                    rows.stream()
                      .map(
                        r ->
                          toResponse(r, null, tagMap.getOrDefault(r.getId(), List.of())))
                      .toList())
                  .totalCount(totalCount)
                  .page(page)
                  .pageSize(pageSize)
                  .totalPages(totalPages)
                  .filterOptions(filterOptions)
                  .build());
        });
  }

  @Override
  public Completable replaceTags(String projectId, long journeyId, List<String> tags) {
    List<String> normalized = AnalysisEntityTags.normalizeOrThrow(tags);
    return journeyDao
      .findByProjectAndId(projectId, journeyId)
      .switchIfEmpty(Maybe.error(ServiceError.JOURNEY_NOT_FOUND.getException()))
      .toSingle()
      .flatMapCompletable(
        ignored ->
          funnelJourneyTagDao.replaceTags(
            projectId, FunnelJourneyTagEntityType.JOURNEY, journeyId, normalized));
  }

  private void validateFilters(List<FunnelAttributeFilter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return;
    }
    for (FunnelAttributeFilter f : filters) {
      if (f.getField() == null || f.getField().isBlank()) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter attribute is required");
      }
      if (f.getOperator() == null) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter operator is required");
      }
      if (CollectionUtils.isEmpty(f.getValue())) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "Filter value must not be empty");
      }
    }
  }

  private void validateTimeBounds(Instant start, Instant end) {
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

  private FunnelType parseOptionalJourneyType(String raw) {
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

  private JourneyResponse toResponse(
    JourneyRow row, JourneyResultsResponse journeyResults, List<String> tags) {
    try {
      List<FunnelAttributeFilter> filters = null;
      if (row.getFiltersJson() != null && !row.getFiltersJson().isBlank()) {
        filters = objectMapper.readValue(row.getFiltersJson(), new TypeReference<>() {
        });
      }
      AnalysisComputedStatus computed =
        AnalysisComputedStatusResolver.compute(
          FunnelType.fromJson(row.getJourneyType()), row.getLatestJobStatus());
      return JourneyResponse.builder()
        .id(row.getId())
        .projectId(row.getProjectId())
        .name(row.getName())
        .description(row.getDescription())
        .status(computed)
        .anchorEvent(row.getAnchorEvent())
        .direction(JourneyDirection.fromJson(row.getDirection()))
        .depth(row.getDepth())
        .mode(FunnelMode.valueOf(row.getMode()))
        .filters(filters)
        .journeyType(FunnelType.fromJson(row.getJourneyType()))
        .startTime(row.getStartTime())
        .endTime(row.getEndTime())
        .expiry(row.getExpiry())
        .dateRangeDays(row.getDateRangeDays())
        .createdAt(row.getCreatedAt())
        .updatedAt(row.getUpdatedAt())
        .createdBy(row.getCreatedBy())
        .journeyResults(journeyResults)
        .tags(tags)
        .build();
    } catch (JsonProcessingException e) {
      log.error("Corrupt journey JSON for id {}", row.getId(), e);
      throw ServiceError.INTERNAL_SERVER_ERROR.getCustomException("Stored journey definition is invalid");
    }
  }
}
