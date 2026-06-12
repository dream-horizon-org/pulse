/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

class TestAndroidInstrumentation : AndroidInstrumentation {
    override val name: String = "test"

    var isInstalled = false
        private set

    override fun install(ctx: InstallationContext) {
        isInstalled = true
    }
}
