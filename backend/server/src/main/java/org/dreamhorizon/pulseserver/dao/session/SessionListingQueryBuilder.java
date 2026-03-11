package org.dreamhorizon.pulseserver.dao.session;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.dreamhorizon.pulseserver.dao.session.SessionListingConstants.*;

/**
 * Builds complete ClickHouse SQL strings for session listing queries.
 * <p>
 * Pure function: takes configuration, returns SQL. No DI dependencies.
 * The generated SQL feeds directly into {@code QueryConfiguration.newQuery(sql)}.
 */
public final class SessionListingQueryBuilder {

    private String projectId;
    private String startTime;
    private String endTime;
    private final List<FilterCondition> whereFilters = new ArrayList<>();
    private final List<FilterCondition> havingFilters = new ArrayList<>();
    private Set<QuickFilter> quickFilters = EnumSet.noneOf(QuickFilter.class);
    private FilterMode filterMode = FilterMode.MATCH_ALL;
    private SortField sortField = SortField.START_TIME;
    private SortDirection sortDirection = SortDirection.DESC;
    private CursorCodec.CursorValue cursor;
    private int limit = 51;
    private String search;

    private SessionListingQueryBuilder() {}

    public static SessionListingQueryBuilder create() {
        return new SessionListingQueryBuilder();
    }

    public SessionListingQueryBuilder projectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public SessionListingQueryBuilder timeRange(String startTime, String endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        return this;
    }

    public SessionListingQueryBuilder filter(FilterField field, Operator operator, Object value) {
        FilterCondition condition = new FilterCondition(field, operator, value);
        if (field.getClauseType() == FilterField.ClauseType.WHERE) {
            whereFilters.add(condition);
        } else {
            havingFilters.add(condition);
        }
        return this;
    }

    public SessionListingQueryBuilder quickFilters(Set<QuickFilter> quickFilters) {
        this.quickFilters = quickFilters != null ? quickFilters : EnumSet.noneOf(QuickFilter.class);
        return this;
    }

    public SessionListingQueryBuilder filterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
        return this;
    }

    public SessionListingQueryBuilder sortBy(SortField field, SortDirection direction) {
        this.sortField = field;
        this.sortDirection = direction;
        return this;
    }

    public SessionListingQueryBuilder cursor(CursorCodec.CursorValue cursor) {
        this.cursor = cursor;
        return this;
    }

    public SessionListingQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SessionListingQueryBuilder search(String search) {
        this.search = search;
        return this;
    }

    /**
     * @return true if any non-MV filter is present, requiring a semi-join subquery on otel_traces.
     */
    public boolean requiresSemiJoin() {
        return whereFilters.stream().anyMatch(f -> !f.field().isInMV());
    }

    /**
     * Builds the session listing query against {@code otel.session_summary}.
     * Includes semi-join subquery when non-MV filters are present.
     */
    public String buildListingQuery() {
        validate();
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(LISTING_SELECT).append('\n');
        sql.append(FROM_SESSION_SUMMARY).append('\n');

        buildWhereClause(sql);

        sql.append(GROUP_BY_SESSION).append('\n');

        buildHavingClause(sql);

        sql.append("ORDER BY ")
                .append(sortField.getExpression()).append(' ').append(sortDirection.getSql())
                .append(", sessionId DESC\n");

        sql.append("LIMIT ").append(limit);

        return sql.toString();
    }

    /**
     * Builds the journey query for a given set of session IDs.
     * Queries {@code otel.otel_traces} using bloom_filter on SessionId.
     */
    public String buildJourneyQuery(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            throw new IllegalArgumentException("sessionIds must not be empty for journey query");
        }
        validate();

        String idList = buildIdList(sessionIds);

        return JOURNEY_SELECT + '\n'
                + "WHERE ProjectId = " + quote(projectId) + '\n'
                + "  AND SessionId IN (" + idList + ")\n"
                + "  AND Timestamp >= " + toDateTime64(startTime) + '\n'
                + "GROUP BY SessionId";
    }

    /**
     * Builds the impacted screens query for a given set of session IDs.
     * Queries {@code otel.stack_trace_events} and returns crash/ANR/non-fatal
     * screen names grouped by session.
     */
    public String buildImpactedScreensQuery(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            throw new IllegalArgumentException("sessionIds must not be empty for impacted screens query");
        }
        validate();

        String idList = buildIdList(sessionIds);

        return IMPACTED_SCREENS_SELECT + '\n'
                + "WHERE ProjectId = " + quote(projectId) + '\n'
                + "  AND SessionId IN (" + idList + ")\n"
                + "  AND Timestamp >= " + toDateTime64(startTime) + '\n'
                + "  AND Timestamp <= " + toDateTime64(endTime) + '\n'
                + "GROUP BY SessionId";
    }

    // -------------------------------------------------------------------------
    // Internal SQL assembly
    // -------------------------------------------------------------------------

    private void buildWhereClause(StringBuilder sql) {
        sql.append("WHERE ProjectId = ").append(quote(projectId)).append('\n');
        sql.append("  AND startTime >= ").append(toDateTime64(startTime)).append('\n');
        sql.append("  AND startTime <= ").append(toDateTime64(endTime)).append('\n');

        if (search != null && !search.isBlank()) {
            String escaped = quote(search);
            sql.append("  AND (userId = ").append(escaped)
                    .append(" OR sessionId = ").append(escaped).append(")\n");
        }

        List<FilterCondition> mvWhereFilters = whereFilters.stream()
                .filter(f -> f.field().isInMV())
                .toList();
        for (FilterCondition fc : mvWhereFilters) {
            sql.append("  AND ").append(fc.toSql()).append('\n');
        }

        if (requiresSemiJoin()) {
            sql.append("  AND sessionId IN (\n");
            buildSemiJoinSubquery(sql);
            sql.append("  )\n");
        }
    }

    private void buildSemiJoinSubquery(StringBuilder sql) {
        List<FilterCondition> nonMvFilters = whereFilters.stream()
                .filter(f -> !f.field().isInMV())
                .toList();

        sql.append("    ").append(SEMI_JOIN_SELECT).append('\n');
        sql.append("    WHERE ProjectId = ").append(quote(projectId)).append('\n');
        sql.append("    AND Timestamp >= ").append(toDateTime64(startTime)).append('\n');
        sql.append("    AND Timestamp <= ").append(toDateTime64(endTime)).append('\n');
        sql.append("    AND SessionId != ''\n");

        for (FilterCondition fc : nonMvFilters) {
            sql.append("    AND ").append(fc.toSql()).append('\n');
        }
    }

    private void buildHavingClause(StringBuilder sql) {
        List<String> conditions = new ArrayList<>();

        if (!quickFilters.isEmpty()) {
            String quickGroup = quickFilters.stream()
                    .map(QuickFilter::getHavingCondition)
                    .collect(Collectors.joining(" OR ", "(", ")"));
            conditions.add(quickGroup);
        }

        if (!havingFilters.isEmpty()) {
            String joiner = " " + filterMode.getSqlOperator() + " ";
            String advancedGroup = havingFilters.stream()
                    .map(FilterCondition::toSql)
                    .collect(Collectors.joining(joiner, "(", ")"));
            conditions.add(advancedGroup);
        }

        if (cursor != null) {
            conditions.add(buildCursorCondition());
        }

        if (!conditions.isEmpty()) {
            sql.append("HAVING ").append(String.join(" AND ", conditions)).append('\n');
        }
    }

    /**
     * Cursor pagination uses tuple comparison:
     * DESC -> ({sortExpr}, sessionId) < ({cursorSortValue}, {cursorSessionId})
     * ASC  -> ({sortExpr}, sessionId) > ({cursorSortValue}, {cursorSessionId})
     */
    private String buildCursorCondition() {
        String sortExpr = sortField.getExpression();
        String op = sortDirection.getCursorOp();
        String sortVal = formatCursorSortValue(cursor.getSortValue());
        String sessionVal = quote(cursor.getSessionId());
        return "(" + sortExpr + ", sessionId) " + op + " (" + sortVal + ", " + sessionVal + ")";
    }

    private String formatCursorSortValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        String strVal = String.valueOf(value);
        if (sortField.isTimestampSort()) {
            return toDateTime64(strVal);
        }
        return Operator.quoteValue(value);
    }

    // -------------------------------------------------------------------------
    // Validation & helpers
    // -------------------------------------------------------------------------

    private void validate() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("projectId is required");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalStateException("timeRange (startTime, endTime) is required");
        }
    }

    private static String buildIdList(List<String> ids) {
        return ids.stream()
                .map(id -> "'" + Operator.escapeString(id) + "'")
                .collect(Collectors.joining(", "));
    }

    private static String quote(String value) {
        return "'" + Operator.escapeString(value) + "'";
    }

    private static String toDateTime64(String value) {
        return "parseDateTime64BestEffort(" + quote(value) + ", 9, 'UTC')";
    }

    private record FilterCondition(FilterField field, Operator operator, Object value) {
        String toSql() {
            return operator.toSql(field.getExpression(), value);
        }
    }
}
