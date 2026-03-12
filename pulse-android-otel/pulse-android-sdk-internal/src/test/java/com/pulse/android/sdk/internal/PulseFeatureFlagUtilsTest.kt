package com.pulse.android.sdk.internal

import android.content.Context
import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkName
import io.mockk.mockk
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PulseFeatureFlagUtilsTest {
    private val mockedContext: Context = mockk()

    private fun buildProcessors(enabledFeatures: List<PulseFeatureName>): PulseSamplingSignalProcessors {
        val features = enabledFeatures.map { PulseSdkConfigFakeUtils.createFakeFeatureConfig(it) }
        val sdkConfig = PulseSdkConfigFakeUtils.createFakeConfig(features = features)
        return PulseSamplingSignalProcessors(
            context = mockedContext,
            sdkConfig = sdkConfig,
            currentSdkName = PulseSdkName.ANDROID_JAVA,
        )
    }

    @Test
    fun `when JAVA_CRASH is disabled, crash instrumentation is suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
        assertThat(config.isSuppressed("crash")).isTrue
        assertThat(config.isSuppressed("anr")).isTrue
    }

    @Test
    fun `when JAVA_CRASH is enabled, crash instrumentation is not suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.JAVA_CRASH)))
        assertThat(config.isSuppressed("crash")).isFalse
        assertThat(config.isSuppressed("anr")).isTrue
    }

    @Test
    fun `when JAVA_ANR is disabled, anr instrumentation is suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
        assertThat(config.isSuppressed("anr")).isTrue
    }

    @Test
    fun `when JAVA_ANR is enabled, anr instrumentation is not suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.JAVA_ANR)))
        assertThat(config.isSuppressed("anr")).isFalse
    }

    @Nested
    inner class `NETWORK_CHANGE feature` {
        @Test
        fun `when NETWORK_CHANGE is disabled, network attributes are disabled`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.shouldIncludeNetworkAttributes()).isFalse
        }

        @Test
        fun `when NETWORK_CHANGE is enabled, network attributes remain enabled`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.NETWORK_CHANGE)))
            assertThat(config.shouldIncludeNetworkAttributes()).isTrue
        }
    }

    @Nested
    inner class `INTERACTION feature` {
        @Test
        fun `when INTERACTION is disabled, interaction instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)).isTrue
        }

        @Test
        fun `when INTERACTION is enabled, interaction instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.INTERACTION)))
            assertThat(config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)).isFalse
        }
    }

    @Nested
    inner class `CUSTOM_EVENTS feature` {
        @Test
        fun `when CUSTOM_EVENTS is disabled, result marks custom events as disabled`() {
            val config = OtelRumConfig()
            val result = PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(result.isCustomEventEnabled).isFalse
        }

        @Test
        fun `when CUSTOM_EVENTS is enabled, result marks custom events as enabled`() {
            val config = OtelRumConfig()
            val result =
                PulseFeatureFlagUtils.apply(
                    config,
                    buildProcessors(enabledFeatures = listOf(PulseFeatureName.CUSTOM_EVENTS)),
                )
            assertThat(result.isCustomEventEnabled).isTrue
        }
    }

    @Test
    fun `when all features are enabled, nothing is suppressed and custom events remain enabled`() {
        val config = OtelRumConfig()
        val allFeatures = PulseFeatureName.values().toList()
        val result = PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = allFeatures))

        assertThat(config.isSuppressed("crash")).isFalse
        assertThat(config.isSuppressed("anr")).isFalse
        assertThat(config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)).isFalse
        assertThat(config.shouldIncludeNetworkAttributes()).isTrue
        assertThat(result.isCustomEventEnabled).isTrue
    }

    @Test
    fun `when JS_CRASH is disabled, no instrumentations are suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(
            config,
            buildProcessors(
                enabledFeatures =
                    PulseFeatureName.values().filter {
                        it !=
                            PulseFeatureName.JS_CRASH
                    },
            ),
        )

        assertThat(config.isSuppressed("crash")).isFalse
        assertThat(config.isSuppressed("anr")).isFalse
        assertThat(config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)).isFalse
    }

    @Test
    fun `when JS_CRASH is enabled, no instrumentations are suppressed`() {
        val config = OtelRumConfig()
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.JS_CRASH)))

        assertThat(config.isSuppressed("crash")).isTrue
        assertThat(config.isSuppressed("anr")).isTrue
    }
}
