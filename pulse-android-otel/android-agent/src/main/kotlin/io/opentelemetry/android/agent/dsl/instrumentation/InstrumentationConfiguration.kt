/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.dsl.instrumentation

import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.SessionReplayConfiguration
import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig

@OpenTelemetryDslMarker
class InstrumentationConfiguration(
    config: OtelRumConfig,
    private val defaultHeaders: Map<String, String>,
    private val interactionUrlProvider: () -> String,
) {
    private val activity: ActivityLifecycleConfiguration by lazy {
        ActivityLifecycleConfiguration(
            config,
        )
    }
    private val fragment: FragmentLifecycleConfiguration by lazy {
        FragmentLifecycleConfiguration(
            config,
        )
    }
    private val anr: AnrReporterConfiguration by lazy { AnrReporterConfiguration(config) }
    private val crash: CrashReporterConfiguration by lazy { CrashReporterConfiguration(config) }
    private val networkMonitoring: NetworkMonitoringConfiguration by lazy {
        NetworkMonitoringConfiguration(
            config,
        )
    }
    private val slowRendering: SlowRenderingReporterConfiguration by lazy {
        SlowRenderingReporterConfiguration(
            config,
        )
    }

    private val interaction: InteractionConfiguration by lazy {
        InteractionConfiguration(
            config,
            defaultHeaders,
            interactionUrlProvider,
        )
    }

    private val viewClick: ViewClickConfiguration by lazy {
        ViewClickConfiguration()
    }

    private val composeClick: ComposeClickConfiguration by lazy {
        ComposeClickConfiguration()
    }
    private val sessionReplay: SessionReplayConfiguration by lazy { SessionReplayConfiguration() }

    fun activity(configure: ActivityLifecycleConfiguration.() -> Unit) {
        activity.configure()
    }

    fun fragment(configure: FragmentLifecycleConfiguration.() -> Unit) {
        fragment.configure()
    }

    fun anrReporter(configure: AnrReporterConfiguration.() -> Unit) {
        anr.configure()
    }

    fun crashReporter(configure: CrashReporterConfiguration.() -> Unit) {
        crash.configure()
    }

    fun networkMonitoring(configure: NetworkMonitoringConfiguration.() -> Unit) {
        networkMonitoring.configure()
    }

    fun slowRenderingReporter(configure: SlowRenderingReporterConfiguration.() -> Unit) {
        slowRendering.configure()
    }

    fun interaction(configure: InteractionConfiguration.() -> Unit) {
        interaction.configure()
    }

    /**
     * View-based click instrumentation. Add the view-click dependency to enable. Use
     * [ViewClickConfiguration.captureContext] to control label extraction (performance).
     */
    fun viewClick(configure: ViewClickConfiguration.() -> Unit) {
        viewClick.configure()
    }

    /**
     * Compose click instrumentation. Add the compose-click dependency to enable. Use
     * [ComposeClickConfiguration.captureContext] to control label extraction (performance).
     */
    fun composeClick(configure: ComposeClickConfiguration.() -> Unit) {
        composeClick.configure()
    }

    fun sessionReplay(configure: SessionReplayConfiguration.() -> Unit) {
        sessionReplay.markConfigured()
        sessionReplay.configure()
    }

    /**
     * Returns the configured [SessionReplayConfig] if [sessionReplay] was invoked in the
     * instrumentations block; null otherwise.
     */
    fun getSessionReplayConfig(): SessionReplayConfig? = sessionReplay.getConfigIfConfigured()
}
