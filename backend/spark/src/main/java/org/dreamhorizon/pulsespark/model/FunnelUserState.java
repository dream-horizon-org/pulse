package org.dreamhorizon.pulsespark.model;

/**
 * One row of {@code otel.funnel_user_state} — per-user funnel rollup for
 * {@code UNIQUE_USERS} funnels.
 *
 * <p>Each row carries a <b>canonical session</b> — the user's session that
 * reached the furthest step (most recent on ties). Drop-off attribution uses
 * this canonical session as the OTel anchor so signals (crash, trace, replay)
 * correlate to one concrete moment rather than a blur across attempts.
 *
 * @param dropoffStep {@code -1} when any of this user's sessions converted;
 *                    otherwise {@code maxReachedStep + 1}.
 */
public record FunnelUserState(
        long funnelId,
        String projectId,
        String runTime,
        String userId,
        int maxReachedStep,
        int dropoffStep,
        String canonicalSessionId,
        long canonicalLastReachedAtEpochSec,
        String canonicalTraceIdAtDropoff,
        String canonicalScreenAtDropoff,
        String appVersion,
        String osName,
        String osVersion,
        String platform,
        String deviceModel,
        String networkProvider,
        String geoCountry,
        int sessionAttempts
) {}
