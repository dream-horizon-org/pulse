/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.common

import io.opentelemetry.api.common.AttributeKey

object RumConstants {
    const val OTEL_RUM_LOG_TAG: String = "OpenTelemetryRum"

    @JvmField
    val LAST_SCREEN_NAME_KEY: AttributeKey<String> = AttributeKey.stringKey("last.screen.name")

    @JvmField
    val SCREEN_NAME_KEY: AttributeKey<String> = AttributeKey.stringKey("screen.name")

    @JvmField
    val START_TYPE_KEY: AttributeKey<String> = AttributeKey.stringKey("start.type")

    @JvmField
    val RUM_SDK_VERSION: AttributeKey<String> = AttributeKey.stringKey("rum.sdk.version")

    @JvmField
    val STORAGE_SPACE_FREE_KEY: AttributeKey<Long> = AttributeKey.longKey("storage.free")

    @JvmField
    val HEAP_FREE_KEY: AttributeKey<Long> = AttributeKey.longKey("heap.free")

    @JvmField
    val BATTERY_PERCENT_KEY: AttributeKey<Double> = AttributeKey.doubleKey("battery.percent")

    const val APP_START_SPAN_NAME: String = "AppStart"

    object App {
        /**
         * See https://opentelemetry.io/docs/specs/semconv/registry/attributes/app/#app-build-id
         */
        @JvmField
        val BUILD_ID: AttributeKey<String> = AttributeKey.stringKey("app.build_id")
        val BUILD_NAME: AttributeKey<String> = AttributeKey.stringKey("app.build_name")
    }

    object Android {
        /**
         * See https://opentelemetry.io/docs/specs/semconv/registry/attributes/android/#android-app-state
         * Can be one of the following `background`, `created` and `foreground`
         */
        @JvmField
        val APP_STATE: AttributeKey<String> = AttributeKey.stringKey("android.app.state")
        /**
         * See https://opentelemetry.io/docs/specs/semconv/registry/attributes/android/#android-os-api-leveld
         */
        @JvmField
        val OS_API_LEVEL: AttributeKey<String> = AttributeKey.stringKey("android.os.api_level")
    }

    object Events {
        const val INIT_EVENT_STARTED: String = "rum.sdk.init.started"
        const val INIT_EVENT_CONFIG: String = "rum.sdk.init.config"
        const val INIT_EVENT_NET_PROVIDER: String = "rum.sdk.init.net.provider"
        const val INIT_EVENT_NET_MONITOR: String = "rum.sdk.init.net.monitor"
        const val INIT_EVENT_ANR_MONITOR: String = "rum.sdk.init.anr_monitor"
        const val INIT_EVENT_JANK_MONITOR: String = "rum.sdk.init.jank_monitor"
        const val INIT_EVENT_CRASH_REPORTER: String = "rum.sdk.init.crash.reporter"
        const val INIT_EVENT_SPAN_EXPORTER: String = "rum.sdk.init.span.exporter"

        // TODO: Use the semconv when available
        const val EVENT_SESSION_START: String = "session.start"
        const val EVENT_SESSION_END: String = "session.end"
    }
}
