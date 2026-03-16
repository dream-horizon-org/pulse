package com.pulse.android.sdk.replay

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SessionReplayRegistryTest {

    @AfterEach
    fun tearDown() {
        SessionReplayRegistry.clearIntegration()
    }

    @Test
    fun `set and getAndClearPending round-trip bootstrap`() {
        val config = SessionReplayConfig()
        val bootstrap = SessionReplayBootstrap(
            config = config,
            projectId = "proj-1",
            userIdProvider = { "user-1" },
        )
        SessionReplayRegistry.set(bootstrap)
        val pending = SessionReplayRegistry.getAndClearPending()
        assertThat(pending).isNotNull
        assertThat(pending!!.projectId).isEqualTo("proj-1")
        assertThat(pending.userIdProvider()).isEqualTo("user-1")
        assertThat(pending.config).isSameAs(config)
        assertThat(SessionReplayRegistry.getAndClearPending()).isNull()
    }

    @Test
    fun `getAndClearPending returns null when nothing set`() {
        SessionReplayRegistry.getAndClearPending() // clear any previous
        assertThat(SessionReplayRegistry.getAndClearPending()).isNull()
    }

    @Test
    fun `setIntegration and getIntegration return same instance`() {
        val integration = mockk<SessionReplayIntegration>(relaxed = true)
        SessionReplayRegistry.setIntegration(integration)
        assertThat(SessionReplayRegistry.getIntegration()).isSameAs(integration)
    }

    @Test
    fun `clearIntegration clears integration`() {
        val integration = mockk<SessionReplayIntegration>(relaxed = true)
        SessionReplayRegistry.setIntegration(integration)
        SessionReplayRegistry.clearIntegration()
        assertThat(SessionReplayRegistry.getIntegration()).isNull()
    }
}
