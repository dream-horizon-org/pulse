/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.compose.click

/**
 * Compose UI is declared [compileOnly] on this module; host apps without
 * `androidx.compose.ui:ui` must not execute code that loads Compose internals.
 *
 * Presence is detected via a stable public API type ([ANDROID_COMPOSE_VIEW_CLASS_NAME]),
 * cached after the first probe.
 */
internal object ComposeClasspathProbe {
    /** When non-null, [isComposeUiPresent] returns this value (tests only). */
    @Volatile
    internal var composeUiPresenceOverride: Boolean? = null

    private val lock = Any()

    @Volatile
    private var cachedPresent: Boolean? = null

    fun isComposeUiPresent(): Boolean {
        composeUiPresenceOverride?.let { return it }
        synchronized(lock) {
            cachedPresent?.let { return it }
            val present =
                try {
                    Class.forName(ANDROID_COMPOSE_VIEW_CLASS_NAME)
                    true
                } catch (_: ClassNotFoundException) {
                    false
                } catch (_: Throwable) {
                    false
                }
            cachedPresent = present
            return present
        }
    }

    private const val ANDROID_COMPOSE_VIEW_CLASS_NAME =
        "androidx.compose.ui.platform.AndroidComposeView"
}
