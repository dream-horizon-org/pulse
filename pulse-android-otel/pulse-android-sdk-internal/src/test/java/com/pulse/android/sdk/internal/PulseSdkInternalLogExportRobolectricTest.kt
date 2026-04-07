/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(io.opentelemetry.android.Incubating::class)

package com.pulse.android.sdk.internal

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.android.api.otel.PulseDataCollectionConsent
import io.opentelemetry.android.agent.connectivity.HttpEndpointConnectivity
import io.opentelemetry.android.agent.session.SessionConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

/**
 * JVM test with Robolectric: calls [PulseSDKInternal.initialize] directly against a local
 * [MockWebServer] (no static holder or custom test runner).
 *
 * OTLP/HTTP export is validated end-to-end in the demo app’s instrumented tests; on the JVM,
 * Robolectric does not reliably deliver OkHttp-backed OTLP POSTs to [MockWebServer] even when
 * [io.opentelemetry.sdk.logs.SdkLoggerProvider.forceFlush] succeeds. This test instead proves config fetch + log emission
 * through the real SDK logger pipeline via a recording [LogRecordProcessor].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class PulseSdkInternalLogExportRobolectricTest {

    private lateinit var server: MockWebServer
    private lateinit var sdk: PulseSDKInternal
    private val capturedLogBodies = CopyOnWriteArrayList<String>()

    private fun recordingLoggerCustomizer(): BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> =
        BiFunction { builder, _ ->
            builder.addLogRecordProcessor(
                object : LogRecordProcessor {
                    override fun onEmit(
                        context: Context,
                        logRecord: ReadWriteLogRecord,
                    ) {
                        logRecord.bodyValue?.asString()?.let { capturedLogBodies.add(it) }
                    }

                    override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()

                    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
                },
            )
        }

    @Before
    fun setUp() {
        capturedLogBodies.clear()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            server = MockWebServer()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path ?: ""
                        if (path.contains("/v1/configs/active")) {
                            return MockResponse().setResponseCode(404)
                        }
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{}")
                    }
                }
            server.start()

            val baseUrl = server.url("/").toString().trimEnd('/')
            val app = ApplicationProvider.getApplicationContext<Application>()
            sdk = PulseSDKInternal()
            sdk.initialize(
                application = app,
                endpointBaseUrl = baseUrl,
                apiKey = "default-project_devkey01",
                dataCollectionState = PulseDataCollectionConsent.ALLOWED,
                endpointHeaders = emptyMap(),
                spanEndpointConnectivity = HttpEndpointConnectivity.forTraces(baseUrl, emptyMap()),
                logEndpointConnectivity = HttpEndpointConnectivity.forLogs(baseUrl, emptyMap()),
                metricEndpointConnectivity = HttpEndpointConnectivity.forMetrics(baseUrl, emptyMap()),
                customEventConnectivity = HttpEndpointConnectivity.forLogs(baseUrl, emptyMap()),
                configEndpointUrl = null,
                resource = null,
                sessionConfig = SessionConfig.withDefaults(),
                globalAttributes = null,
                beforeSendData = null,
                diskBuffering = null,
                tracerProviderCustomizer = null,
                loggerProviderCustomizer = recordingLoggerCustomizer(),
                instrumentations = null,
            )
        }
    }

    @After
    fun tearDown() {
        if (::sdk.isInitialized && sdk.getOtelOrNull() != null) {
            sdk.shutdown()
            shadowOf(Looper.getMainLooper()).idle()
        }
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun initializeAndEmitEvent_deliversLogsThroughSdkPipelineAndFetchesConfig() {
        val pipelineDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < pipelineDeadline && server.requestCount == 0) {
            Thread.sleep(50)
        }
        assertTrue(
            "Timed out waiting for config HTTP (background config refresh)",
            server.requestCount > 0,
        )

        val otel = sdk.getOtelOrThrow().getOpenTelemetry()
        assertTrue(otel is OpenTelemetrySdk)
        val sdkOtel = otel as OpenTelemetrySdk

        val eventBody = "robolectric-test-body"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val rumLogger =
                sdkOtel.sdkLoggerProvider.loggerBuilder("io.opentelemetry.rum.events").build()
            val recordBuilder = rumLogger.logRecordBuilder()
            assertFalse(
                "RUM logger must not use API noop LogRecordBuilder (${recordBuilder.javaClass.name})",
                recordBuilder.javaClass.name.contains("Noop"),
            )
            sdk.getOtelOrThrow().emitEvent(
                "pulse.internal.robolectric.log",
                eventBody,
                Attributes.empty(),
            )
        }

        val logFlush = sdkOtel.sdkLoggerProvider.forceFlush()
        logFlush.join(30, TimeUnit.SECONDS)
        assertTrue("LoggerProvider forceFlush did not succeed", logFlush.isSuccess)

        assertTrue(
            "Expected emitEvent body to pass through SdkLoggerProvider processors (see class KDoc for OTLP/HTTP on Robolectric)",
            capturedLogBodies.any { it.contains(eventBody) },
        )
    }
}
