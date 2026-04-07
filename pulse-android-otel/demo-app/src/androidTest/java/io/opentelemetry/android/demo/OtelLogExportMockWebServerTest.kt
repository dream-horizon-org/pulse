/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private const val LOG_EVENT_NAME = "android.test.log.export"

/**
 * Drains the server until an OTLP logs POST is seen or [timeoutSeconds] elapses.
 * Only blocks inside [MockWebServer.takeRequest] (no [Thread.sleep] polling). A timed-out
 * [takeRequest] ([RecordedRequest] null) retries until the overall deadline.
 */
private fun MockWebServer.drainUntilOtlpLogsPost(timeoutSeconds: Long): Pair<RecordedRequest?, List<String>> {
    return measureTimedValue {
        val pathsSeen = mutableListOf<String>()
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadlineNanos) {
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
            if (remainingMs <= 0) break
            val req =
                takeRequest(
                    remainingMs.coerceAtLeast(1L),
                    TimeUnit.MILLISECONDS,
                ) ?: continue
            pathsSeen.add("${req.method} ${req.path}")
            if (req.path?.contains("/v1/logs") == true) {
                return@measureTimedValue req to pathsSeen
            }
        }
        null to pathsSeen
    }.also {
        println("drainUntilOtlpLogsPost took ${it.duration}")
    }.value
}

@RunWith(AndroidJUnit4::class)
class OtelLogExportMockWebServerTest {

    @Test
    fun emitEvent_resultsInOtlpLogsPostToMockWebServer() {
        assertTrue(false)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val server = DemoMockWebServerHolder.server

        OtelDemoApplication.rum.emitEvent(LOG_EVENT_NAME, "instrumented-test-body", Attributes.empty())

        val otel = OtelDemoApplication.rum.getOpenTelemetry()
        assertTrue("Expected SDK OpenTelemetry instance", otel is OpenTelemetrySdk)
        (otel as OpenTelemetrySdk).sdkLoggerProvider.forceFlush().join(30, TimeUnit.SECONDS)

        val (logsRequest, pathsSeen) = server.drainUntilOtlpLogsPost(timeoutSeconds = 45)

        assertNotNull(
            "Expected an HTTP request to OTLP logs path /v1/logs. Paths observed: $pathsSeen",
            logsRequest,
        )
        assertEquals("POST", logsRequest!!.method)
    }
}
