package com.pulse.android.sdk.internal

import android.content.Context
import com.pulse.sampling.core.exporters.PulseSamplingSignalProcessors
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkName
import io.mockk.mockk
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.interaction.library.InteractionInstrumentation
import io.opentelemetry.sdk.metrics.SdkMeterProvider
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
            meterProviderLazy = lazy { SdkMeterProvider.builder().build() },
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
    fun `when JAVA_CRASH is enabled and was locally suppressed, crash instrumentation is re-allowed`() {
        val config = OtelRumConfig()
        config.suppressInstrumentation("crash")
        PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.JAVA_CRASH)))
        assertThat(config.isSuppressed("crash")).isFalse
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

    @Test
    fun `when JAVA_ANR is enabled and was locally suppressed, anr instrumentation is re-allowed`() {
        val config = OtelRumConfig()
        config.suppressInstrumentation("anr")
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

        @Test
        fun `when NETWORK_CHANGE is enabled and was locally disabled, network attributes are re-enabled`() {
            val config = OtelRumConfig()
            config.disableNetworkAttributes()
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

        @Test
        fun `when INTERACTION is enabled and was locally suppressed, interaction instrumentation is re-allowed`() {
            val config = OtelRumConfig()
            config.suppressInstrumentation(InteractionInstrumentation.INSTRUMENTATION_NAME)
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.INTERACTION)))
            assertThat(config.isSuppressed(InteractionInstrumentation.INSTRUMENTATION_NAME)).isFalse
        }
    }

    @Nested
    inner class `CLICK feature` {
        @Test
        fun `when CLICK is disabled, view and compose click instrumentation are suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("view.click")).isTrue
            assertThat(config.isSuppressed("compose.click")).isTrue
        }

        @Test
        fun `when CLICK is enabled, view and compose click instrumentation are not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.CLICK)))
            assertThat(config.isSuppressed("view.click")).isFalse
            assertThat(config.isSuppressed("compose.click")).isFalse
        }

        @Test
        fun `when CLICK is enabled and was locally suppressed, view and compose click instrumentation are re-allowed`() {
            val config = OtelRumConfig()
            config.suppressInstrumentation("view.click")
            config.suppressInstrumentation("compose.click")
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.CLICK)))
            assertThat(config.isSuppressed("view.click")).isFalse
            assertThat(config.isSuppressed("compose.click")).isFalse
        }
    }

    @Nested
    inner class `ANDROID_ACTIVITY feature` {
        @Test
        fun `when ANDROID_ACTIVITY is disabled, activity instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("activity")).isTrue
        }

        @Test
        fun `when ANDROID_ACTIVITY is enabled, activity instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_ACTIVITY)))
            assertThat(config.isSuppressed("activity")).isFalse
        }

        @Test
        fun `when ANDROID_ACTIVITY is enabled and was locally suppressed, activity instrumentation is re-allowed`() {
            val config = OtelRumConfig()
            config.suppressInstrumentation("activity")
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_ACTIVITY)))
            assertThat(config.isSuppressed("activity")).isFalse
        }
    }

    @Nested
    inner class `ANDROID_FRAGMENT feature` {
        @Test
        fun `when ANDROID_FRAGMENT is disabled, fragment instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("fragment")).isTrue
        }

        @Test
        fun `when ANDROID_FRAGMENT is enabled, fragment instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_FRAGMENT)))
            assertThat(config.isSuppressed("fragment")).isFalse
        }

        @Test
        fun `when ANDROID_FRAGMENT is enabled and was locally suppressed, fragment instrumentation is re-allowed`() {
            val config = OtelRumConfig()
            config.suppressInstrumentation("fragment")
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_FRAGMENT)))
            assertThat(config.isSuppressed("fragment")).isFalse
        }
    }

    @Nested
    inner class `ANDROID_SLOWRENDERING feature` {
        @Test
        fun `when ANDROID_SLOWRENDERING is disabled, slow rendering instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("slowrendering")).isTrue
        }

        @Test
        fun `when ANDROID_SLOWRENDERING is enabled, slow rendering instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_SLOWRENDERING)))
            assertThat(config.isSuppressed("slowrendering")).isFalse
        }

        @Test
        fun `when ANDROID_SLOWRENDERING is enabled and was locally suppressed, slow rendering instrumentation is re-allowed`() {
            val config = OtelRumConfig()
            config.suppressInstrumentation("slowrendering")
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = listOf(PulseFeatureName.ANDROID_SLOWRENDERING)))
            assertThat(config.isSuppressed("slowrendering")).isFalse
        }
    }

    @Nested
    inner class `MEMORY feature` {
        @Test
        fun `when MEMORY is disabled, memory instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("memory")).isTrue
        }

        @Test
        fun `when MEMORY is enabled, memory instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(
                config,
                buildProcessors(enabledFeatures = listOf(PulseFeatureName.MEMORY)),
            )
            assertThat(config.isSuppressed("memory")).isFalse
        }
    }

    @Nested
    inner class `BATTERY feature` {
        @Test
        fun `when BATTERY is disabled, battery instrumentation is suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(config, buildProcessors(enabledFeatures = emptyList()))
            assertThat(config.isSuppressed("battery")).isTrue
        }

        @Test
        fun `when BATTERY is enabled, battery instrumentation is not suppressed`() {
            val config = OtelRumConfig()
            PulseFeatureFlagUtils.apply(
                config,
                buildProcessors(enabledFeatures = listOf(PulseFeatureName.BATTERY)),
            )
            assertThat(config.isSuppressed("battery")).isFalse
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
        assertThat(config.isSuppressed("view.click")).isFalse
        assertThat(config.isSuppressed("compose.click")).isFalse
        assertThat(config.isSuppressed("activity")).isFalse
        assertThat(config.isSuppressed("fragment")).isFalse
        assertThat(config.isSuppressed("slowrendering")).isFalse
        assertThat(config.shouldIncludeNetworkAttributes()).isTrue
        assertThat(config.isSuppressed("memory")).isFalse
        assertThat(config.isSuppressed("battery")).isFalse
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
