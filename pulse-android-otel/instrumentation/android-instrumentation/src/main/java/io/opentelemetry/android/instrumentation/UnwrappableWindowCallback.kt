/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation

import android.view.Window

/**
 * Callback wrapper that can unwrap to restore the original.
 * Used by click instrumentations (view, compose) so they can fully restore the chain on pause.
 */
interface UnwrappableWindowCallback : Window.Callback {
    fun unwrap(): Window.Callback
}

object WindowCallbackUnwrap {
    /**
     * Fully unwraps all [UnwrappableWindowCallback] layers. Ensures the original callback is
     * restored when both View and Compose click instrumentations are active.
     */
    @JvmStatic
    fun fullyUnwrap(callback: Window.Callback): Window.Callback {
        var current: Window.Callback = callback
        while (current is UnwrappableWindowCallback) {
            current = current.unwrap()
        }
        return current
    }
}
