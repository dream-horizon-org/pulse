/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.test.runner.AndroidJUnitRunner
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Starts a [MockWebServer] before the application [Application.onCreate] so Pulse SDK OTLP and
 * config traffic can be handled locally during instrumentation tests.
 */
@Suppress("unused") // used as test runner
class DemoMockWebServerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        val server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    // Avoid returning 200 + "{}" for active config: that decodes to PulseSdkConfig defaults and
                    // enables PulseSamplingSignalProcessors, which can filter OTLP log exports for the session.
                    if (path.contains("/v1/configs/active")) {
                        return MockResponse().setResponseCode(404)
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{}")
                }
            }
        val previousPolicy = StrictMode.getThreadPolicy()
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(previousPolicy).permitNetwork().build(),
            )
            server.start()
        } finally {
            StrictMode.setThreadPolicy(previousPolicy)
        }
        DemoMockWebServerHolder.server = server
        val url = server.url("/").toString().trimEnd('/')
        OtelDemoInstrumentationHooks.otlpEndpointBaseUrl = url
        OtelDemoInstrumentationHooks.interactionConfigUrl = "$url/v1/interaction-configs/"
        return super.newApplication(cl, className, context)
    }
}
