@file:Suppress("RedundantVisibilityModifier", "unused") // explicit api requires public modifier mentioned

package com.pulse.sampling.models

import com.pulse.sampling.models.matchers.PulseSignalMatchCondition

public object PulseSdkConfigFakeUtils {
    public fun createFakeConfig(
        version: Int = 1,
        sessionSampleRate: Float = 1.0f,
        scheduleDurationMs: Long = 1000L,
        collectorUrl: String = "https://example.com/",
        configUrl: String = "https://example.com/config/",
        beforeInitQueueSize: Int = 100,
        filterMode: PulseSignalFilterMode = PulseSignalFilterMode.WHITELIST,
        signalFilters: List<PulseSignalMatchCondition> = listOf(createFakeSignalMatchCondition()),
        attributesToDrop: List<PulseSignalMatchCondition> = emptyList(),
    ): PulseSdkConfig =
        PulseSdkConfig(
            version = version,
            description = "This is test description",
            sampling =
                PulseSamplingConfig(
                    default =
                        PulseDefaultSamplingConfig(
                            sessionSampleRate = sessionSampleRate,
                        ),
                    rules = emptyList(),
                ),
            signals =
                PulseSignalConfig(
                    scheduleDurationMs = scheduleDurationMs,
                    logsCollectorUrl = collectorUrl + "v1/logs/",
                    spanCollectorUrl = collectorUrl + "v1/spans/",
                    metricCollectorUrl = collectorUrl + "v1/metrics/",
                    attributesToDrop = attributesToDrop,
                    filters =
                        PulseSignalFilter(
                            mode = filterMode,
                            values = signalFilters,
                        ),
                ),
            interaction =
                PulseInteractionConfig(
                    collectorUrl = collectorUrl,
                    configUrl = configUrl,
                    beforeInitQueueSize = beforeInitQueueSize,
                ),
            features = emptyList(),
        )

    public fun createFakeSignalMatchCondition(
        name: String = ".*",
        props: Set<PulseProp> = emptySet(),
        scopes: Set<PulseSignalScope> = setOf(PulseSignalScope.TRACES, PulseSignalScope.LOGS),
        sdks: Set<PulseSdkName> = setOf(PulseSdkName.CURRENT_SDK_NAME),
    ): PulseSignalMatchCondition =
        PulseSignalMatchCondition(
            name = name,
            props = props,
            scopes = scopes,
            sdks = sdks,
        )

    public fun createFakeProp(
        name: String = "fake-prop-name",
        value: String? = "fake-prop-value",
    ): PulseProp =
        PulseProp(
            name = name,
            value = value,
        )
}
