package com.pulse.android.sdk.replay

import android.util.Log
import com.pulse.android.sdk.replay.events.ReplayEvent

/**
 * Simple [ReplayEventEmitter] that logs each batch to Logcat.
 * Useful for manual testing and debugging. Use in debug builds only.
 *
 * Example:
 * ```
 * val emitter = LoggingReplayEventEmitter(tag = "Replay")
 * val replay = SessionReplayIntegration(context, config, emitter)
 * replay.install()
 * replay.start(resumeCurrent = false)
 * ```
 */
public class LoggingReplayEventEmitter(
    private val tag: String = "PulseReplay",
    private val minLogLevel: Int = Log.DEBUG,
) : ReplayEventEmitter {

    override fun emit(sessionId: String, events: List<ReplayEvent>) {
        if (events.isEmpty()) return
        if (Log.isLoggable(tag, minLogLevel)) {
            Log.println(minLogLevel, tag, "Emitted sessionId=$sessionId ${events.size} events: ${events.map { it.type.name }}")
            events.forEach { e ->
                Log.println(minLogLevel, tag, "  ${e.type} @ ${e.timestamp} data=${e.data?.javaClass?.simpleName}")
            }
        }
    }
}
