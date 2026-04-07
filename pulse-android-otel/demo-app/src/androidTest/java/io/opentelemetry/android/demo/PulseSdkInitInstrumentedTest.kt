/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.android.sdk.PulseSDK
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseSdkInitInstrumentedTest {

    @Test
    fun pulseSdkInitializesViaProductionApplication() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        assertTrue(
            "Demo must use OtelDemoApplication so production PulseSDK.initialize runs in onCreate",
            app is OtelDemoApplication,
        )
        assertTrue(
            "PulseSDK must report initialized after Application.onCreate",
            PulseSDK.INSTANCE.isInitialized(),
        )
        val rumFromSdk = PulseSDK.INSTANCE.getOtelOrThrow()
        assertNotNull(rumFromSdk.getOpenTelemetry())
        assertSame(
            "OpenTelemetryRum from SDK must match production companion after init",
            rumFromSdk,
            OtelDemoApplication.rum,
        )
    }
}
