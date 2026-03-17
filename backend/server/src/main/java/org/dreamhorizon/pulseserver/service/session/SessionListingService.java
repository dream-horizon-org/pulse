package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.session.CursorCodec;
import org.dreamhorizon.pulseserver.dao.session.FilterField;
import org.dreamhorizon.pulseserver.dao.session.FilterMode;
import org.dreamhorizon.pulseserver.dao.session.Operator;
import org.dreamhorizon.pulseserver.dao.session.QuickFilter;
import org.dreamhorizon.pulseserver.dao.session.SessionListingQueryBuilder;
import org.dreamhorizon.pulseserver.dao.session.SortDirection;
import org.dreamhorizon.pulseserver.dao.session.SortField;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.session.models.AdvancedFilterGroup;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConditionRequest;
import org.dreamhorizon.pulseserver.resources.session.models.FiltersRequest;
import org.dreamhorizon.pulseserver.resources.session.models.ImpactedScreensRow;
import org.dreamhorizon.pulseserver.resources.session.models.JourneyRow;
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

        SortField activeSortField = resolveSortField(request.getSortBy());

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

                    String journeySql = builder.buildJourneyQuery(sessionIds);
                    String impactedScreensSql = builder.buildImpactedScreensQuery(sessionIds);
                    log.debug("Journey SQL: {}", journeySql);
                    log.debug("Impacted screens SQL: {}", impactedScreensSql);

                    return Single.zip(
                            executeJourneyQuery(journeySql, projectId),
                            executeImpactedScreensQuery(impactedScreensSql, projectId),
                            (journeyRows, screenRows) -> buildResponse(
                                    pageRows, journeyRows, screenRows,
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
            Set<QuickFilter> qf = filters.getQuick().stream()
                    .map(name -> safeEnum(QuickFilter.class, name, "quick filter"))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(QuickFilter.class)));
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
            FilterMode mode = "OR".equals(op) ? FilterMode.MATCH_ANY : FilterMode.MATCH_ALL;
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
                FilterField field = safeEnum(FilterField.class, fc.getField(), "filter field");
                Operator operator = safeEnum(Operator.class, fc.getOperator(), "operator");
                builder.filter(field, operator, fc.getValue());
            }
        }
    }

    private SortField resolveSortField(String sortBy) {
        if (sortBy != null && !sortBy.isBlank()) {
            return safeEnum(SortField.class, sortBy, "sortBy");
        }
        return SortField.START_TIME;
    }

    private void applySorting(SessionListingQueryBuilder builder, String sortBy, String sortDirection) {
        if (sortBy != null && !sortBy.isBlank()) {
            SortField field = safeEnum(SortField.class, sortBy, "sortBy");
            SortDirection dir = SortDirection.DESC;
            if (sortDirection != null && !sortDirection.isBlank()) {
                dir = safeEnum(SortDirection.class, sortDirection, "sortDirection");
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
                builder.cursor(CursorCodec.decode(cursor));
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
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, SessionRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    private Single<List<JourneyRow>> executeJourneyQuery(String sql, String tenantId) {
        QueryConfiguration config = QueryConfiguration.newQuery(sql)
                .timeoutMs(TIMEOUT_MS)
                .tenantId(tenantId)
                .projectId(tenantId)
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, JourneyRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    private Single<List<ImpactedScreensRow>> executeImpactedScreensQuery(String sql, String tenantId) {
        QueryConfiguration config = QueryConfiguration.newQuery(sql)
                .timeoutMs(TIMEOUT_MS)
                .tenantId(tenantId)
                .projectId(tenantId)
                .build();
        return clickhouseQueryService.executeQueryOrCreateJob(config, ImpactedScreensRow.class)
                .map(result -> result.getRows() != null ? result.getRows() : Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Response assembly
    // -------------------------------------------------------------------------

    private SessionListingResponse buildResponse(
            List<SessionRow> rows,
            List<JourneyRow> journeyRows,
            List<ImpactedScreensRow> screenRows,
            boolean hasMore,
            int pageSize,
            SortField activeSortField
    ) {
        Map<String, List<String>> journeyMap = journeyRows.stream()
                .collect(Collectors.toMap(
                        JourneyRow::getSessionId,
                        row -> parseJourney(row.getJourney()),
                        (a, b) -> a
                ));

        Map<String, Map<String, List<String>>> screensMap = screenRows.stream()
                .collect(Collectors.toMap(
                        ImpactedScreensRow::getSessionId,
                        SessionListingService::toScreensMap,
                        (a, b) -> a
                ));

        List<SessionItem> sessions = rows.stream()
                .map(row -> SessionItem.builder()
                        .sessionId(row.getSessionId())
                        .startTime(row.getStartTime())
                        .durationMs(row.getDurationMs())
                        .user(row.getUser())
                        .qualityScore(row.getQualityScore())
                        .issues(buildIssues(row))
                        .platform(row.getPlatform())
                        .spanCount(row.getSpanCount())
                        .journey(journeyMap.getOrDefault(row.getSessionId(), Collections.emptyList()))
                        .impactedScreens(screensMap.getOrDefault(row.getSessionId(), null))
                        .build())
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasMore) {
            SessionRow lastRow = rows.get(rows.size() - 1);
            Object sortValue = activeSortField.getCursorValueExtractor().apply(lastRow);
            nextCursor = CursorCodec.encode(sortValue, lastRow.getSessionId());
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

    private static List<String> parseJourney(String delimited) {
        if (delimited == null || delimited.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(delimited.split("\\Q" + JOURNEY_DELIMITER + "\\E", -1));
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
