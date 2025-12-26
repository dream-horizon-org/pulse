package com.pulsereactnativeotel

import java.util.concurrent.atomic.AtomicReference

/**
 * Allows React Native to override Android Activity/Fragment-based screen tracking.
 */
internal object ReactNativeScreenNameTracker {
    private val currentScreenName = AtomicReference<String?>()

    fun setCurrentScreenName(screenName: String?) {
        currentScreenName.set(screenName)
    }

    fun getCurrentScreenName(): String? = currentScreenName.get()
}

