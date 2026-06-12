package org.dreamhorizon.pulsespark.model;

import java.util.List;

/**
 * One row of {@code otel.funnel_dropoff_attribution} — precomputed (step × cause) ranking
 * with cohort sizes, affected counts, lift, and a capped {@code ExampleSessions} array.
 *
 * <p>Spark's emitter mirrors the ClickHouse compute path's
 * {@code ClickHouseFunnelComputeDao.buildAttributionInsertSql} so the side-panel can read
 * from the same precomputed table regardless of which engine ran the funnel.
 *
 * @param stepIndex      The "step the user failed to reach" — for SESSIONS funnels this is
 *                       the bridge row's {@code DropoffStep}; for UNIQUE_USERS the user
 *                       rollup's {@code DropoffStep}.
 * @param causeKind      One of {@code crash | anr | non_fatal | http_5xx | http_4xx |
 *                       frozen_frame} (Spark v1 emits the first five).
 * @param causeKey       Human-readable cause identifier (e.g. {@code OutOfMemoryError@Checkout}
 *                       for crashes, {@code POST checkout 503} for HTTP errors).
 * @param dropoffCohort  Total droppers at this step (sessions for SESSIONS mode, users for
 *                       UNIQUE_USERS).
 * @param converterCohort Total converters (reached final step) — baseline denominator for lift.
 * @param lift           {@code (DropoffAffected/DropoffCohort) / (ConverterAffected/ConverterCohort)}.
 *                       Sentinel {@code 999.0} when converter cohort or affected count is 0
 *                       and droppers have non-zero affected count.
 * @param pValue         Stub {@code 0.0} for v1 — chi-square / Fisher's exact deferred,
 *                       matches the CH compute path.
 * @param exampleSessions Up to 50 canonical session IDs for evidence drill-in.
 */
public record FunnelAttributionRow(
        long funnelId,
        String projectId,
        String runTime,
        int stepIndex,
        String causeKind,
        String causeKey,
        String causeLabel,
        long dropoffCohort,
        long dropoffAffected,
        long converterCohort,
        long converterAffected,
        double lift,
        double pValue,
        List<String> exampleSessions
) {}
