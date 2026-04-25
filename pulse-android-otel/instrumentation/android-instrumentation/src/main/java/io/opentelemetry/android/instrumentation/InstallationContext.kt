/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

import android.app.Application
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.api.OpenTelemetry
import java.util.Objects

class InstallationContext(
    val application: Application,
    val openTelemetry: OpenTelemetry,
    val sessionProvider: SessionProvider,
    val meteredSessionProvider: SessionProvider? = null,
    /**
     * When true, native `device.crash` is not emitted for
     * `com.facebook.react.common.JavascriptException` — the JS error handler already reports it,
     * avoiding duplicates. Set to true only for React Native builds; defaults to false so pure
     * Android Java apps never silently drop crashes.
     */
    val ignoreJavaScriptExceptions: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstallationContext) return false
        return application == other.application &&
            openTelemetry == other.openTelemetry &&
            sessionProvider == other.sessionProvider &&
            meteredSessionProvider == other.meteredSessionProvider &&
            ignoreJavaScriptExceptions == other.ignoreJavaScriptExceptions
    }

    override fun hashCode(): Int =
        Objects.hash(
            application,
            openTelemetry,
            sessionProvider,
            meteredSessionProvider,
            ignoreJavaScriptExceptions,
        )

    override fun toString(): String =
        "InstallationContext(application=$application, openTelemetry=$openTelemetry, " +
            "sessionProvider=$sessionProvider, meteredSessionProvider=${meteredSessionProvider ?: "null"}, " +
            "ignoreJavaScriptExceptions=$ignoreJavaScriptExceptions)"
}
