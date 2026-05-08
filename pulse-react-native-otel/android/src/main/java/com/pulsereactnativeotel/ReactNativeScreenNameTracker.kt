package com.pulsereactnativeotel

import com.pulse.android.sdk.replay.SessionReplayRegistry
import java.util.concurrent.atomic.AtomicReference

/**
 * Allows React Native to override Android Activity/Fragment-based screen tracking.
 */
internal object ReactNativeScreenNameTracker {
    private val currentScreenName = AtomicReference<String?>()

    fun setCurrentScreenName(screenName: String?) {
        val previous = currentScreenName.getAndSet(screenName)
        if (previous != screenName) {
            SessionReplayRegistry.getIntegration()?.notifyScreenChange()
        }
    }

    fun getCurrentScreenName(): String? = currentScreenName.get()
}

