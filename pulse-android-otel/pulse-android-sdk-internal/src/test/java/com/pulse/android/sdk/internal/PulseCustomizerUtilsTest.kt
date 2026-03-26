package com.pulse.android.sdk.internal

import android.app.Application
import io.mockk.mockk
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.function.BiFunction

class PulseCustomizerUtilsTest {
    private val application: Application = mockk()

    @Nested
    inner class TracerCustomizers {
        @Test
        fun `when no external customizer is provided, returns the internal customizer as-is`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val builder = mockk<SdkTracerProviderBuilder>(relaxed = true)

            val merged = PulseCustomizerUtils.mergeTracerCustomizers(internal = internal, external = null)
            merged.apply(builder, application)

            assertThat(callLog).containsExactly("internal")
        }

        @Test
        fun `when external customizer is provided, internal runs before external`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val external =
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { b, _ ->
                    callLog += "external"
                    b
                }
            val builder = mockk<SdkTracerProviderBuilder>(relaxed = true)

            val merged = PulseCustomizerUtils.mergeTracerCustomizers(internal = internal, external = external)
            merged.apply(builder, application)

            assertThat(callLog).containsExactly("internal", "external")
        }

        @Test
        fun `when external customizer is absent, external is never called`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val builder = mockk<SdkTracerProviderBuilder>(relaxed = true)

            PulseCustomizerUtils.mergeTracerCustomizers(internal = internal, external = null).apply(builder, application)

            assertThat(callLog).doesNotContain("external")
        }

        @Test
        fun `external customizer receives the builder returned by the internal customizer`() {
            val sentinel = mockk<SdkTracerProviderBuilder>(relaxed = true)
            val internal = BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { _, _ -> sentinel }
            var externalReceivedBuilder: SdkTracerProviderBuilder? = null
            val external =
                BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> { b, _ ->
                    externalReceivedBuilder = b
                    b
                }

            val merged = PulseCustomizerUtils.mergeTracerCustomizers(internal = internal, external = external)
            merged.apply(mockk(relaxed = true), application)

            assertThat(externalReceivedBuilder).isSameAs(sentinel)
        }
    }

    @Nested
    inner class LoggerCustomizers {
        @Test
        fun `when no external customizer is provided, returns the internal customizer as-is`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val builder = mockk<SdkLoggerProviderBuilder>(relaxed = true)

            val merged = PulseCustomizerUtils.mergeLoggerCustomizers(internal = internal, external = null)
            merged.apply(builder, application)

            assertThat(callLog).containsExactly("internal")
        }

        @Test
        fun `when external customizer is provided, internal runs before external`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val external =
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { b, _ ->
                    callLog += "external"
                    b
                }
            val builder = mockk<SdkLoggerProviderBuilder>(relaxed = true)

            val merged = PulseCustomizerUtils.mergeLoggerCustomizers(internal = internal, external = external)
            merged.apply(builder, application)

            assertThat(callLog).containsExactly("internal", "external")
        }

        @Test
        fun `when external customizer is absent, external is never called`() {
            val callLog = mutableListOf<String>()
            val internal =
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { b, _ ->
                    callLog += "internal"
                    b
                }
            val builder = mockk<SdkLoggerProviderBuilder>(relaxed = true)

            PulseCustomizerUtils.mergeLoggerCustomizers(internal = internal, external = null).apply(builder, application)

            assertThat(callLog).doesNotContain("external")
        }

        @Test
        fun `external customizer receives the builder returned by the internal customizer`() {
            val sentinel = mockk<SdkLoggerProviderBuilder>(relaxed = true)
            val internal = BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { _, _ -> sentinel }
            var externalReceivedBuilder: SdkLoggerProviderBuilder? = null
            val external =
                BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> { b, _ ->
                    externalReceivedBuilder = b
                    b
                }

            val merged = PulseCustomizerUtils.mergeLoggerCustomizers(internal = internal, external = external)
            merged.apply(mockk(relaxed = true), application)

            assertThat(externalReceivedBuilder).isSameAs(sentinel)
        }
    }
}
