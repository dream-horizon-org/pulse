/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.crash

internal class CrashReportingExceptionHandler(
    private val crashProcessor: (details: CrashDetails) -> Unit,
    private val postCrashAction: () -> Unit,
    private val ignoreJavaScriptExceptions: Boolean = false,
    private val existingHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        if (!ignoreJavaScriptExceptions || throwable.javaClass.name != JAVASCRIPT_EXCEPTION_CLASS_NAME) {
            crashProcessor(CrashDetails(thread, throwable))
            // do our best to make sure the crash makes it out of the VM
            postCrashAction()
        }

        // preserve any existing behavior
        existingHandler?.uncaughtException(thread, throwable)
    }

    private companion object {
        const val JAVASCRIPT_EXCEPTION_CLASS_NAME: String = "com.facebook.react.common.JavascriptException"
    }
}
