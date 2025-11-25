/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(Incubating::class)

package io.opentelemetry.android.demo

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.pulse.android.sdk.PulseSDK
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

const val TAG = "otel.demo"

class OtelDemoApplication : Application() {

    @OptIn(Incubating::class)
    @SuppressLint("RestrictedApi")
    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Initializing the opentelemetry-android-agent")

        rum = initOTel(this)

        // This is needed to get R8 missing rules warnings.
        initializeOtelWithGrpc()
    }

    private fun initOTel(application: Application): OpenTelemetryRum =
        runCatching {
            PulseSDK.INSTANCE.initialize(
                application = application,
                endpointBaseUrl = "http://10.0.2.2:4318",
                globalAttributes = {
                    Attributes.of(AttributeKey.stringKey("demo-version"), "test")
                },
                sessionConfig = SessionConfig(
                    backgroundInactivityTimeout = 2.minutes,
                    maxLifetime = 1.days
                ),
            ) {
                interaction {
                    enabled(true)
                }
                activity {
                    enabled(true)
                }
                fragment {
                    enabled(true)
                }
            }
            PulseSDK.INSTANCE.getOtelOrThrow()
        }.onFailure {
            Log.e(TAG, "Initialization failed", it)
        }.getOrThrow()

    // This is not used but it's needed to verify that our consumer proguard rules cover this use case.
    private fun initializeOtelWithGrpc() {
        val builder = OpenTelemetryRum.builder(this)
            .addSpanExporterCustomizer {
                OtlpGrpcSpanExporter.builder().build()
            }
            .addLogRecordExporterCustomizer {
                OtlpGrpcLogRecordExporter.builder().build()
            }

        // This is an overly-cautious measure to prevent R8 from discarding away the whole method
        // in case it identifies that it's actually not doing anything meaningful.
        if (System.currentTimeMillis() < 0) {
            print(builder)
        }
    }

    companion object {
        lateinit var rum: OpenTelemetryRum

        fun tracer(name: String): Tracer? {
            return rum.getOpenTelemetry().tracerProvider?.get(name)
        }

        fun counter(name: String): LongCounter? {
            return rum.getOpenTelemetry().meterProvider?.get("demo.app")?.counterBuilder(name)
                ?.build()
        }

        fun eventBuilder(scopeName: String, eventName: String): LogRecordBuilder {
            val logger = rum.getOpenTelemetry().logsBridge.loggerBuilder(scopeName).build()
            return logger.logRecordBuilder().setEventName(eventName)
        }

        fun logEvent(
            log: String,
            scopeName: String = "PulseSdk",
            builder: LogRecordBuilder.() -> LogRecordBuilder = { this }
        ) {
            Log.d(RumConstants.OTEL_RUM_LOG_TAG, "logEvent called with log = $log")
            val logger = rum.getOpenTelemetry().logsBridge.loggerBuilder(scopeName).build()
            logger.logRecordBuilder().setBody(log).builder().emit()
        }
    }
}
