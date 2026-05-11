/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import com.pulse.jni.PulseJniCall

@PulseJniCall
internal object DemoNativeCrash {
    init {
        System.loadLibrary("demo_native_crash")
    }

    fun abortNow() {
        nativeAbort()
    }

    fun sigsegvNow() {
        nativeSigsegv()
    }

    @PulseJniCall
    private external fun nativeAbort()

    @PulseJniCall
    private external fun nativeSigsegv()
}
