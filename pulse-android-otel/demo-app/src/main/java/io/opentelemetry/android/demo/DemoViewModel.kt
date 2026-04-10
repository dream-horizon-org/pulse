/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URI

class DemoViewModel : ViewModel() {
    val sessionIdState = MutableStateFlow("? unknown ?")
    private val _networkMessage = MutableSharedFlow<String>()
    val networkMessage: SharedFlow<String> = _networkMessage.asSharedFlow()
    private val tracer = OtelDemoApplication.tracer("otel.demo")!!

    init {
        viewModelScope.launch {
            while (true) {
                delay(5000)
                // TODO: Do some work here maybe
            }
        }
    }

    private fun updateSession() {
        // TODO
    }

    private fun sendTrace(
        type: String,
        value: Float,
    ) {
        // A metric should be a better fit, but for now we're using spans.
        tracer.spanBuilder(type).setAttribute("value", value.toDouble()).startSpan().end()
    }

    fun performSomeWork() {
        DemoWork().startWork()
    }

    /**
     * Blocks the current thread for ~10 seconds. Call from the main (UI) thread to trigger an ANR.
     */
    fun triggerAnr() {
        Thread.sleep(10_000)
    }

    fun makeNetworkCall() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val connection =
                    URI.create("https://httpbin.org/get").toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val code = connection.responseCode
                connection.disconnect()
                _networkMessage.emit("Network call completed (HTTP $code)")
            } catch (e: Exception) {
                _networkMessage.emit("Network call failed: ${e.message}")
            }
        }
    }

}
