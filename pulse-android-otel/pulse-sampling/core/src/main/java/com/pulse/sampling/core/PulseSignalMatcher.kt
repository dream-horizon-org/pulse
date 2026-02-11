package com.pulse.sampling.core

import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.utils.filter
import com.pulse.utils.matchesFromRegexCache
import io.opentelemetry.api.common.Attributes

public fun interface PulseSignalMatcher {
    public fun matches(
        scope: PulseSignalScope,
        name: String,
        props: Attributes,
        signalMatchConfig: PulseSignalMatchCondition,
        sdkName: PulseSdkName,
    ): Boolean
}

@Suppress("FunctionName")
internal fun PulseSignalsAttrMatcher() =
    PulseSignalMatcher { signalScope, signalName, signalProps, signalMatchConfig, currentSdkName ->
        if (
            !(
                signalMatchConfig.sdks.contains(currentSdkName) &&
                    signalMatchConfig.scopes.contains(signalScope) &&
                    signalName.matchesFromRegexCache(signalMatchConfig.name)
            )
        ) {
            return@PulseSignalMatcher false
        }

        val configPropsMap = signalMatchConfig.props.associate { it.name to it.value }
        val signalPropsFiltered = signalProps.filter { it.key in configPropsMap.keys }

        if (signalMatchConfig.props.size != signalPropsFiltered.size()) {
            return@PulseSignalMatcher false
        }

        var isMatched = true

        signalPropsFiltered.forEach { signalPropKey, signalPropValue ->
            if (!isMatched) return@forEach

            val configProp = configPropsMap[signalPropKey.key]

            isMatched =
                if (configProp == null || signalPropValue == null) {
                    signalPropValue == configProp
                } else {
                    signalPropValue.toString().matchesFromRegexCache(configProp)
                }
        }
        isMatched
    }
