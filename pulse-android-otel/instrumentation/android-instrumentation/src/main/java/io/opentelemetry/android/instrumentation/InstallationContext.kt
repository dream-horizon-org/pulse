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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstallationContext) return false
        return application == other.application &&
            openTelemetry == other.openTelemetry &&
            sessionProvider == other.sessionProvider &&
            meteredSessionProvider == other.meteredSessionProvider
    }

    override fun hashCode(): Int = Objects.hash(application, openTelemetry, sessionProvider, meteredSessionProvider)

    override fun toString(): String =
        "InstallationContext(application=$application, openTelemetry=$openTelemetry, " +
            "sessionProvider=$sessionProvider, meteredSessionProvider=${meteredSessionProvider ?: "null"})"
}
