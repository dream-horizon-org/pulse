package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.session.SessionListingCursorCodec;
import org.dreamhorizon.pulseserver.dao.session.SessionListingFilterField;
import org.dreamhorizon.pulseserver.dao.session.SessionListingFilterMode;
import org.dreamhorizon.pulseserver.dao.session.SessionListingOperator;
import org.dreamhorizon.pulseserver.dao.session.SessionListingQuickFilter;
import org.dreamhorizon.pulseserver.dao.session.SessionListingQueryBuilder;
import org.dreamhorizon.pulseserver.dao.session.SessionListingSortDirection;
import org.dreamhorizon.pulseserver.dao.session.SessionListingSortField;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.session.models.AdvancedFilterGroup;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConditionRequest;
import org.dreamhorizon.pulseserver.resources.session.models.FiltersRequest;
import org.dreamhorizon.pulseserver.resources.session.models.ImpactedInteractionsRow;
import org.dreamhorizon.pulseserver.resources.session.models.ImpactedScreensRow;
import org.dreamhorizon.pulseserver.resources.session.models.PageRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingResponse.PageResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingResponse.SessionItem;
import org.dreamhorizon.pulseserver.resources.session.models.SessionRow;
import org.dreamhorizon.pulseserver.resources.session.models.IssueItem;
import org.dreamhorizon.pulseserver.resources.session.models.IssueType;
import org.dreamhorizon.pulseserver.resources.session.models.TimeRangeRequest;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.dreamhorizon.pulseserver.dao.session.SessionListingConstants.JOURNEY_DELIMITER;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionListingService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;
    private static final int TIMEOUT_MS = 5000;

    private final ClickhouseQueryService clickhouseQueryService;

    public Single<SessionListingResponse> getSessionListing(SessionListingRequest request) {
        validateRequest(request);

        String projectId = ProjectContext.requireProjectId();
        int pageSize = resolvePageSize(request.getPage());

        log.info("Session listing: projectId={}, timeRange=[{} .. {}], pageSize={}, sort={}:{}, "
                        + "quickFilters={}, advancedFilters={}, search={}",
                projectId,
                request.getTimeRange().getFrom(), request.getTimeRange().getTo(),
                pageSize,
                request.getSortBy(), request.getSortDirection(),
                request.getFilters() != null && request.getFilters().getQuick() != null
                        ? request.getFilters().getQuick().size() : 0,
                request.getFilters() != null && request.getFilters().getAdvanced() != null
                        && request.getFilters().getAdvanced().getChildren() != null
                        ? request.getFilters().getAdvanced().getChildren().size() : 0,
                request.getQuery() != null ? "present" : "none");

        SessionListingSortField activeSortField = resolveSortField(request.getSortBy());

        SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
                .projectId(projectId)
                .timeRange(request.getTimeRange().getFrom(), request.getTimeRange().getTo())
                .limit(pageSize + 1);

        applyFilters(builder, request.getFilters());
        applySorting(builder, request.getSortBy(), request.getSortDirection());
        applySearch(builder, request.getQuery());
        applyCursor(builder, request.getPage());

        String listingSql = builder.buildListingQuery();
        log.debug("Session listing SQL: {}", listingSql);

        return executeListingQuery(listingSql, projectId)
                .flatMap(rows -> {
                    boolean hasMore = rows.size() > pageSize;
                    List<SessionRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

                    if (pageRows.isEmpty()) {
                        return Single.just(SessionListingResponse.builder()
                                .sessions(Collections.emptyList())
                                .page(PageResponse.builder()
                                        .limit(pageSize)
                                        .hasMore(false)
                                        .build())
                                .build());
                    }

                    List<String> sessionIds = pageRows.stream()
                            .map(SessionRow::getSessionId)
                            .collect(Collectors.toList());

                    String impactedScreensSql = builder.buildImpactedScreensQuery(sessionIds);
                    String impactedInteractionsSql = builder.buildImpactedInteractionsQuery(sessionIds);
                    log.debug("Impacted screens SQL: {}", impactedScreensSql);
                    log.debug("Impacted interactions SQL: {}", impactedInteractionsSql);

                    return Single.zip(
                            executeImpactedScreensQuery(impactedScreensSql, projectId),
                            executeImpactedInteractionsQuery(impactedInteractionsSql, projectId),
                            (screenRows, interactionRows) -> buildResponse(
                                    pageRows, screenRows, interactionRows,
                                    hasMore, pageSize, activeSortField)
                    );
                })
                .onErrorResumeNext(error -> {
                    if (error instanceof jakarta.ws.rs.WebApplicationException) {
                        return Single.error(error);
                    }
                    log.error("Session listing query failed for projectId={}", projectId, error);
                    return Single.error(
                            ServiceError.DATABASE_ERROR.getCustomException(
                                    "Session listing query failed", error.getMessage()));
                });
    }

    // -------------------------------------------------------------------------
    // Request validation
    // -------------------------------------------------------------------------

    private void validateRequest(SessionListingRequest request) {
        TimeRangeRequest tr = request.getTimeRange();
        if (tr == null) {
            throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS
                    .getCustomException("Missing required field: timeRange");
        }
        if (tr.getFrom() == null || tr.getFrom().isBlank()) {
            throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS
                    .getCustomException("Missing required field: timeRange.from");
        }
        if (tr.getTo() == null || tr.getTo().isBlank()) {
            throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS
                    .getCustomException("Missing required field: timeRange.to");
        }
    }

    // -------------------------------------------------------------------------
    // Request -> QueryBuilder wiring
    // -------------------------------------------------------------------------

    private void applyFilters(SessionListingQueryBuilder builder, FiltersRequest filters) {
        if (filters == null) {
            return;
        }

        if (filters.getQuick() != null && !filters.getQuick().isEmpty()) {
            Set<SessionListingQuickFilter> qf = filters.getQuick().stream()
                    .map(name -> safeEnum(SessionListingQuickFilter.class, name, "quick filter"))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(SessionListingQuickFilter.class)));
            builder.quickFilters(qf);
        }

        AdvancedFilterGroup advanced = filters.getAdvanced();
        if (advanced == null) {
            return;
        }

        if (advanced.getOp() != null && !advanced.getOp().isBlank()) {
            String op = advanced.getOp().toUpperCase();
            if (!"AND".equals(op) && !"OR".equals(op)) {
                throw ServiceError.INVALID_REQUEST_PARAM
                        .getCustomException("Invalid filter op: '" + advanced.getOp()
                                + "'. Must be AND or OR");
            }
            SessionListingFilterMode mode = "OR".equals(op) ? SessionListingFilterMode.MATCH_ANY : SessionListingFilterMode.MATCH_ALL;
            builder.filterMode(mode);
        }

        if (advanced.getChildren() != null) {
            for (FilterConditionRequest fc : advanced.getChildren()) {
                if (fc.getField() == null || fc.getField().isBlank()) {
                    throw ServiceError.INVALID_REQUEST_PARAM
                            .getCustomException("Filter condition missing 'field'");
                }
                if (fc.getOperator() == null || fc.getOperator().isBlank()) {
                    throw ServiceError.INVALID_REQUEST_PARAM
                            .getCustomException("Filter condition missing 'operator'");
                }
                SessionListingFilterField field = safeEnum(SessionListingFilterField.class, fc.getField(), "filter field");
                SessionListingOperator operator = safeEnum(SessionListingOperator.class, fc.getOperator(), "operator");
                builder.filter(field, operator, fc.getValue());
            }
        }
    }

    private SessionListingSortField resolveSortField(String sortBy) {
        if (sortBy != null && !sortBy.isBlank()) {
            return safeEnum(SessionListingSortField.class, sortBy, "sortBy");
        }
        return SessionListingSortField.START_TIME;
    }

    private void applySorting(SessionListingQueryBuilder builder, String sortBy, String sortDirection) {
        if (sortBy != null && !sortBy.isBlank()) {
            SessionListingSortField field = safeEnum(SessionListingSortField.class, sortBy, "sortBy");
            SessionListingSortDirection dir = SessionListingSortDirection.DESC;
            if (sortDirection != null && !sortDirection.isBlank()) {
                dir = safeEnum(SessionListingSortDirection.class, sortDirection, "sortDirection");
            }
            builder.sortBy(field, dir);
        }
    }

    private void applySearch(SessionListingQueryBuilder builder, String query) {
        if (query != null && !query.isBlank()) {
            builder.search(query.trim());
        }
    }

    private void applyCursor(SessionListingQueryBuilder builder, PageRequest page) {
        if (page == null) {
            return;
        }
        String cursor = page.getCursor();
        if (cursor != null && !cursor.isBlank()) {
            try {
                builder.cursor(SessionListingCursorCodec.decode(cursor));
            } catch (IllegalArgumentException e) {
                throw ServiceError.INVALID_REQUEST_PARAM
                        .getCustomException("Invalid cursor: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query execution
    // -------------------------------------------------------------------------

    private Single<List<SessionRow>> executeListingQuery(String sql, String tenantId) {
        QueryConfiguration config = QueryConfiguration.newQuery(sql)
                .timeoutMs(TIMEOUT_MS)
                .tenantId(tenantId)
                .projectId(tenantId)
                .useQueryConditionCache(true)
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, SessionRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    private Single<List<ImpactedScreensRow>> executeImpactedScreensQuery(String sql, String tenantId) {
        QueryConfiguration config = QueryConfiguration.newQuery(sql)
                .timeoutMs(TIMEOUT_MS)
                .tenantId(tenantId)
                .projectId(tenantId)
                .useQueryConditionCache(true)
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, ImpactedScreensRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    private Single<List<ImpactedInteractionsRow>> executeImpactedInteractionsQuery(String sql, String tenantId) {
        QueryConfiguration config = QueryConfiguration.newQuery(sql)
                .timeoutMs(TIMEOUT_MS)
                .tenantId(tenantId)
                .projectId(tenantId)
                .useQueryConditionCache(true)
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, ImpactedInteractionsRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Response assembly
    // -------------------------------------------------------------------------

    private SessionListingResponse buildResponse(
            List<SessionRow> rows,
            List<ImpactedScreensRow> screenRows,
            List<ImpactedInteractionsRow> interactionRows,
            boolean hasMore,
            int pageSize,
            SessionListingSortField activeSortField
    ) {
        Map<String, Map<String, List<String>>> screensMap = screenRows.stream()
                .collect(Collectors.toMap(
                        ImpactedScreensRow::getSessionId,
                        SessionListingService::toScreensMap,
                        (a, b) -> a
                ));

        Map<String, List<String>> interactionsMap = interactionRows.stream()
                .collect(Collectors.toMap(
                        ImpactedInteractionsRow::getSessionId,
                        row -> parseDelimited(row.getImpactedInteractionNames()),
                        (a, b) -> a
                ));

        List<SessionItem> sessions = rows.stream()
                .map(row -> SessionItem.builder()
                        .sessionId(row.getSessionId())
                        .startTime(row.getStartTime())
                        .endTime(row.getEndTime())
                        .durationMs(row.getDurationMs())
                        .user(row.getUser())
                        .qualityScore(row.getQualityScore())
                        .issues(buildIssues(row))
                        .platform(row.getPlatform())
                        .spanCount(row.getSpanCount())
                        .journey(Collections.emptyList())
                        .impactedScreens(screensMap.getOrDefault(row.getSessionId(), null))
                        .impactedInteractions(interactionsMap.getOrDefault(row.getSessionId(), Collections.emptyList()))
                        .build())
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasMore) {
            SessionRow lastRow = rows.get(rows.size() - 1);
            Object sortValue = activeSortField.getCursorValueExtractor().apply(lastRow);
            nextCursor = SessionListingCursorCodec.encode(sortValue, lastRow.getSessionId());
        }

        return SessionListingResponse.builder()
                .sessions(sessions)
                .page(PageResponse.builder()
                        .limit(pageSize)
                        .nextCursor(nextCursor)
                        .hasMore(hasMore)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int resolvePageSize(PageRequest page) {
        if (page == null || page.getLimit() == null || page.getLimit() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(page.getLimit(), MAX_LIMIT);
    }

    private static Map<String, List<String>> toScreensMap(ImpactedScreensRow row) {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        List<String> crashes = parseDelimited(row.getCrashScreens());
        List<String> anrs = parseDelimited(row.getAnrScreens());
        List<String> nonFatals = parseDelimited(row.getNonFatalScreens());
        if (!crashes.isEmpty()) map.put("crashes", crashes);
        if (!anrs.isEmpty()) map.put("anrs", anrs);
        if (!nonFatals.isEmpty()) map.put("nonFatals", nonFatals);
        return map.isEmpty() ? null : map;
    }

    /**
     * Builds an ordered list of non-zero issue items from a SessionRow.
     * Order follows IssueType enum declaration (severity descending).
     */
    private static List<IssueItem> buildIssues(SessionRow row) {
        List<IssueItem> issues = new ArrayList<>();
        addIfPositive(issues, IssueType.CRASH, row.getCrashCount());
        addIfPositive(issues, IssueType.ANR, row.getAnrCount());
        addIfPositive(issues, IssueType.NETWORK_ERROR, row.getNetworkErrors());
        addIfPositive(issues, IssueType.NON_FATAL, row.getNonFatal());
        addIfPositive(issues, IssueType.INTERACTION_ERROR, row.getInteractionErrors());
        addIfPositive(issues, IssueType.SLOW_INTERACTION, row.getSlowInteractionCount());
        addIfPositiveFraction(issues, IssueType.FROZEN_FRAME, row.getFrozenFrameCount());
        return issues;
    }

    private static void addIfPositive(List<IssueItem> issues, IssueType type, Long count) {
        if (count != null && count > 0) {
            issues.add(IssueItem.builder()
                    .type(type.name())
                    .label(type.getLabel())
                    .count(count)
                    .build());
        }
    }

    private static void addIfPositiveFraction(List<IssueItem> issues, IssueType type, Double count) {
        if (count != null && count > 0) {
            issues.add(IssueItem.builder()
                    .type(type.name())
                    .label(type.getLabel())
                    .count(count)
                    .build());
        }
    }

    private static List<String> parseDelimited(String delimited) {
        if (delimited == null || delimited.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(delimited.split("\\Q" + JOURNEY_DELIMITER + "\\E", -1));
    }

    /**
     * Safely resolves a string to an enum value, throwing a 400 with a descriptive
     * message listing all valid values when the input doesn't match.
     */
    private static <E extends Enum<E>> E safeEnum(Class<E> enumClass, String value, String label) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw ServiceError.INVALID_REQUEST_PARAM
                    .getCustomException("Unknown " + label + ": '" + value
                            + "'. Valid values: " + valid);
        }
    }
}
