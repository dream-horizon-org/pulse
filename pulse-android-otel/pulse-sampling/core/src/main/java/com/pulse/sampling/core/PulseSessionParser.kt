package com.pulse.sampling.core

import android.content.Context
import com.pulse.sampling.models.PulseSamplingConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.SamplingRate

public fun interface PulseSessionParser {
    public fun parses(
        context: Context,
        samplingConfig: PulseSamplingConfig,
        currentSdkName: PulseSdkName,
    ): SamplingRate

    public companion object {
        internal val alwaysOn = PulseSessionParser { _, _, _ -> 1F }
        internal val alwaysOff = PulseSessionParser { _, _, _ -> 0F }
    }
}

@Suppress("FunctionName")
internal fun PulseSessionConfigParser() =
    PulseSessionParser { context, samplingConfig, currentSdkName ->
        samplingConfig.rules
            .firstOrNull {
                currentSdkName in it.sdks && it.matches(context)
            }?.sessionSampleRate ?: samplingConfig.default.sessionSampleRate
    }
