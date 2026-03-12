package com.pulse.android.sdk.internal

import android.app.Application
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import java.util.function.BiFunction

/**
 * Merges an internal customizer (always applied first) with an optional external customizer.
 *
 * The internal customizer runs first so that external integrations (e.g. React Native) can
 * further decorate the already-configured builder. Order matters; the internal processors
 * must be in place before the external ones stack on top.
 */
internal object PulseCustomizerUtils {
    internal fun mergeTracerCustomizers(
        internal: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>,
        external: BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder>?,
    ): BiFunction<SdkTracerProviderBuilder, Application, SdkTracerProviderBuilder> =
        if (external != null) {
            BiFunction { builder, app ->
                val withInternal = internal.apply(builder, app)
                external.apply(withInternal, app)
            }
        } else {
            internal
        }

    internal fun mergeLoggerCustomizers(
        internal: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>,
        external: BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder>?,
    ): BiFunction<SdkLoggerProviderBuilder, Application, SdkLoggerProviderBuilder> =
        if (external != null) {
            BiFunction { builder, app ->
                val withInternal = internal.apply(builder, app)
                external.apply(withInternal, app)
            }
        } else {
            internal
        }
}
