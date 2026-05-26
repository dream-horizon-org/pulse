/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.agent.session.SessionIdTimeoutHandler
import io.opentelemetry.android.internal.services.Services
import io.opentelemetry.android.internal.services.applifecycle.AppLifecycle
import io.opentelemetry.android.internal.services.storage.CacheStorage
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenState
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenTracker
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenTelemetryRumInitializerTest {
    private lateinit var appLifecycle: AppLifecycle

    @Before
    fun setUp() {
        appLifecycle = mockk(relaxed = true)
    }

    @OptIn(Incubating::class)
    @Test
    fun `Verify timeoutHandler initialization`() {
        createAndSetServiceManager()

        OpenTelemetryRumInitializer.initialize(
            RuntimeEnvironment.getApplication(),
            true,
            "http://127.0.0.1:4318",
        )

        verify {
            appLifecycle.registerListener(any<SessionIdTimeoutHandler>())
        }
    }

    @After
    fun tearDown() {
        Services.set(null)
    }

    private fun createAndSetServiceManager(): Services {
        val services = mockk<Services>()
        every { services.appLifecycle }.returns(appLifecycle)
        every { services.currentNetworkProvider }.returns(mockk(relaxed = true))
        every { services.cacheStorage }.returns(
            mockk<CacheStorage>().apply {
                every { cacheDir }.returns(File(""))
            },
        )
        val visibleScreenTracker = mockk<VisibleScreenTracker>()
        every { visibleScreenTracker.visibleScreenState }.returns(
            MutableStateFlow(VisibleScreenState("unknown", null, null, null, null, null)),
        )
        every { services.visibleScreenTracker }.returns(visibleScreenTracker)
        Services.set(services)
        return services
    }
}
