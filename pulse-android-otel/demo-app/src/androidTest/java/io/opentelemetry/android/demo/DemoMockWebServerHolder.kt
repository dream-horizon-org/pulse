/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import okhttp3.mockwebserver.MockWebServer

object DemoMockWebServerHolder {
    lateinit var server: MockWebServer
}
