/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.activity

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.max

/**
 * Tracks the foreground/background state of the application based on activity lifecycle events.
 * This class tracks when activities enter and leave the foreground to determine the overall app state.
 * Uses the same approach as androidx.lifecycle.ProcessLifecycleOwner to avoid reporting
 * background/foreground changes during configuration changes (rotation).
 */
internal class ForegroundBackgroundTracker(
    private val logger: Logger,
) {
    private val foregroundActivities: Deque<String> = ArrayDeque()
    private val activityInstanceIds: MutableMap<Activity, String> = mutableMapOf()
    private val mainThreadHandler =
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == MSG_SEND_BACKGROUND) {
                handleBackgroundMessage()
                true
            } else {
                false
            }
        }

    /**
     * The number of started Activity instances: `onActivityStarted` - `onActivityStopped`
     */
    private var startedActivityCount: Int = 0

    private var isWaitingForActivityRestart: Boolean = false
    private var lastExitedForegroundMs: Long = 0
    private var hasEmittedCreatedEvent: Boolean = false

    fun onActivityStarted(activity: Activity) {
        val activityName = activity.javaClass.name
        synchronized(foregroundActivities) {
            if (startedActivityCount == 0 && !isWaitingForActivityRestart) {
                if (hasEmittedCreatedEvent) {
                    // Emit foreground event if we were in background
                    if (lastExitedForegroundMs > 0) {
                        emitAppStateEvent(APP_STATE_FOREGROUND_NAME)
                        lastExitedForegroundMs = 0
                    }
                } else {
                    // Emit created event on first activity start
                    emitAppStateEvent(APP_STATE_CREATED_NAME)
                    hasEmittedCreatedEvent = true
                    lastExitedForegroundMs = 0
                }
            }

            startedActivityCount++
            mainThreadHandler.removeMessages(MSG_SEND_BACKGROUND)
            isWaitingForActivityRestart = false

            // Track activity instance
            val activityId =
                activityInstanceIds.getOrPut(activity) {
                    "$activityName@${System.identityHashCode(activity)}"
                }
            if (!foregroundActivities.contains(activityId)) {
                foregroundActivities.add(activityId)
            }
        }
    }

    fun onActivityStopped(activity: Activity) {
        val currentTimeMs = System.currentTimeMillis()
        val activityId = activityInstanceIds[activity]
        if (activityId != null) {
            synchronized(foregroundActivities) {
                foregroundActivities.removeLastOccurrence(activityId)
                startedActivityCount = max(0, startedActivityCount - 1)

                if (startedActivityCount == 0 && hasEmittedCreatedEvent) {
                    if (activity.isChangingConfigurations) {
                        // isChangingConfigurations indicates that the Activity will be restarted
                        // immediately, but we post a slightly delayed Message to handle cases
                        // where (for whatever reason) that doesn't happen
                        // this follows the same logic as ProcessLifecycleOwner
                        isWaitingForActivityRestart = true
                        mainThreadHandler.sendMessageDelayed(
                            mainThreadHandler.obtainMessage(MSG_SEND_BACKGROUND),
                            BACKGROUND_TIMEOUT_MS,
                        )
                    } else {
                        emitAppStateEvent(APP_STATE_BACKGROUND_NAME)
                        lastExitedForegroundMs = currentTimeMs
                    }
                }

                // Remove from tracking if not waiting for restart
                if (!isWaitingForActivityRestart) {
                    activityInstanceIds.remove(activity)
                }
            }
        }
    }

    private fun handleBackgroundMessage() {
        isWaitingForActivityRestart = false
        synchronized(foregroundActivities) {
            if (startedActivityCount == 0 && hasEmittedCreatedEvent) {
                emitAppStateEvent(APP_STATE_BACKGROUND_NAME)
                lastExitedForegroundMs = System.currentTimeMillis()
                // Clean up any remaining activity references
                activityInstanceIds.clear()
            }
        }
    }

    private fun emitAppStateEvent(state: String) {
        val attributes =
            Attributes
                .builder()
                .put(RumConstants.Android.APP_STATE, state)
                .build()

        logger
            .logRecordBuilder()
            .setEventName(DEVICE_APP_LIFECYCLE_EVENT_NAME)
            .setAllAttributes(attributes)
            .emit()
    }

    companion object {
        /**
         * Same as `androidx.lifecycle.ProcessLifecycleOwner` and is used to avoid reporting
         * background / foreground changes when there is only 1 Activity being restarted for configuration
         * changes.
         */
        @VisibleForTesting
        internal const val BACKGROUND_TIMEOUT_MS = 700L
        private const val MSG_SEND_BACKGROUND = 1
        private const val DEVICE_APP_LIFECYCLE_EVENT_NAME = "device.app.lifecycle"
        private const val APP_STATE_CREATED_NAME = "created"
        private const val APP_STATE_FOREGROUND_NAME = "foreground"
        private const val APP_STATE_BACKGROUND_NAME = "background"
    }
}
