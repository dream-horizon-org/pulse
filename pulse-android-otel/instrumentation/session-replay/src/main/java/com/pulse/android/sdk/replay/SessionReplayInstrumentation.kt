package com.pulse.android.sdk.replay

import android.util.Log
import com.google.auto.service.AutoService
import com.pulse.android.sdk.replay.events.ReplayEvent
import com.pulse.android.sdk.replay.internal.ReplayEnvelopeBuilder
import com.pulse.android.sdk.replay.internal.ReplayLog
import com.pulse.android.sdk.replay.remote.SessionReplayApiClient
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import java.io.File

/**
 * Session replay as an [AndroidInstrumentation]. The SDK registers only [SessionReplayBootstrap]
 * (config + projectId + userIdProvider) before RUM is built; this instrumentation builds
 * the emitter, API client, and integration internally.
 */
@AutoService(AndroidInstrumentation::class)
public class SessionReplayInstrumentation : AndroidInstrumentation {

    override val name: String = INSTRUMENTATION_NAME

    override fun install(ctx: InstallationContext) {
        val bootstrap = SessionReplayRegistry.getAndClearPending() ?: return
        val application = ctx.application
        val config = bootstrap.config

        val storageDir = File(application.filesDir, STORAGE_DIR_NAME)
        val replayApiClient = config.replayApiBaseUrl?.let { SessionReplayApiClient(baseUrl = it) }

        val buildEnvelope: (String, List<ReplayEvent>) -> String = { sessionId, events ->
            ReplayEnvelopeBuilder.buildEnvelope(
                sessionId = sessionId,
                events = events,
                projectId = bootstrap.projectId,
                userId = bootstrap.userIdProvider().ifEmpty { "anonymous" },
            )
        }

        val sendReplayPayload: (String) -> Result<Unit> = { payload ->
            if (replayApiClient != null) {
                logSendingPayload(payload)
                replayApiClient.sendBatch(payload).also { result ->
                    val sessionIdsLog = ReplayEnvelopeBuilder.getSessionIdsForLog(payload)
                    val suffix = if (sessionIdsLog != null) " — $sessionIdsLog" else ""
                    result.onSuccess { Log.i(ReplayLog.TAG, "Session replay upload succeeded$suffix") }
                    result.onFailure { t -> Log.e(ReplayLog.TAG, "Session replay upload failed$suffix", t) }
                }
            } else {
                logPayloadNoApiUrl(payload)
                Result.success(Unit)
            }
        }

        val persistingEmitter = PersistingReplayEmitter(
            storageDir = storageDir,
            buildEnvelope = buildEnvelope,
            realSend = sendReplayPayload,
            flushIntervalSeconds = config.flushIntervalSeconds,
            flushAt = config.flushAt,
            maxBatchSize = config.maxBatchSize,
            replayStorageEncryption = DefaultReplayStorageEncryption(application),
            logger = {},
        )
        persistingEmitter.sendCachedEvents()

        val integration = SessionReplayIntegration(
            context = application,
            config = config,
            eventEmitter = persistingEmitter,
            sessionIdProvider = { ctx.sessionProvider.getSessionId() },
            logger = {},
        )
        integration.install()
        integration.start(resumeCurrent = false)
        SessionReplayRegistry.setIntegration(integration)
    }

    override fun uninstall(ctx: InstallationContext) {
        SessionReplayRegistry.getIntegration()?.uninstall()
        SessionReplayRegistry.clearIntegration()
    }

    private fun logSendingPayload(payload: String) {
        val payloadSizeKb = payload.length / 1024
        val isBatched = payload.trimStart().startsWith("[") && payload.contains("},{")
        val eventTypesSummary = ReplayEnvelopeBuilder.getEventTypesSummary(payload)
        val sessionIdsLog = ReplayEnvelopeBuilder.getSessionIdsForLog(payload)
        val parts = buildList {
            add("$payloadSizeKb KB (${payload.length} bytes)")
            if (isBatched) add("[batched request]") else add("[single envelope]")
            sessionIdsLog?.let { add("— $it") }
            eventTypesSummary?.let { add("— event types: $it") }
        }
        Log.d(ReplayLog.TAG, "[Replay flow] Sending to backend: ${parts.joinToString(" ")}")
    }

    private fun logPayloadNoApiUrl(payload: String) {
        val payloadSizeKb = payload.length / 1024
        val sessionIdsLog = ReplayEnvelopeBuilder.getSessionIdsForLog(payload)
        Log.d(ReplayLog.TAG, "Session replay payload (no API URL): $payloadSizeKb KB (${payload.length} bytes)${if (sessionIdsLog != null) " — $sessionIdsLog" else ""}")
        if (payload.length <= MAX_LOG_LEN) {
            Log.d(ReplayLog.TAG, "Replay payload: $payload")
        } else {
            var offset = 0
            var part = 0
            while (offset < payload.length) {
                val chunk = payload.substring(offset, (offset + MAX_LOG_LEN).coerceAtMost(payload.length))
                Log.d(ReplayLog.TAG, "Replay payload part ${++part}: $chunk")
                offset += MAX_LOG_LEN
            }
        }
    }

    public companion object {
        public const val INSTRUMENTATION_NAME: String = "session_replay"
        private const val STORAGE_DIR_NAME = "pulse_replay"
        private const val MAX_LOG_LEN = 4000
    }
}
