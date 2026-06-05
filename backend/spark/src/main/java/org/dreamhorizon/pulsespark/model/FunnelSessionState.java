package org.dreamhorizon.pulsespark.model;

/**
 * One row of {@code otel.funnel_session_state} — per-session funnel position.
 *
 * <p>Emitted for every session that entered a funnel in a given run, regardless
 * of the funnel's {@code mode}. This is the bridge table that lets drop-off
 * attribution join funnel state against OTel session-scoped signals
 * (crashes, traces, session replay, heatmaps).
 *
 * @param dropoffStep {@code -1} when this session converted (reached the final
 *                    step); otherwise {@code lastReachedStep + 1}.
 */
public record FunnelSessionState(
        long funnelId,
        String projectId,
        String runTime,
        String sessionId,
        String userId,
        int lastReachedStep,
        String lastReachedStepName,
        long lastReachedAtEpochSec,
        int dropoffStep,
        long timeToDropoffSec,
        String screenAtDropoff,
        String traceIdAtDropoff,
        String appVersion,
        String osName,
        String osVersion,
        String platform,
        String deviceModel,
        String networkProvider,
        String geoCountry
) {}
