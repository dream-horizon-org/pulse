package com.pulse.sampling.core

import android.content.Context
import com.pulse.sampling.models.PulseSamplingConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.SamplingRate

public fun interface PulseSessionParser {
    public fun parses(
        context: Context,
        samplingConfig: PulseSamplingConfig,
    ): SamplingRate
}

@Suppress("FunctionName")
internal fun PulseSessionConfigParser() =
    PulseSessionParser { context, samplingConfig ->
        samplingConfig.rules
            .firstOrNull {
                PulseSdkName.CURRENT_SDK_NAME in it.sdks && it.matches(context)
            }?.sessionSampleRate ?: samplingConfig.default.sessionSampleRate
    }
