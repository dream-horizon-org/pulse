package com.pulse.sampling.core

import com.pulse.otel.utils.matchesFromRegexCache
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition

public fun interface PulseSignalMatcher {
    public fun matches(
        scope: PulseSignalScope,
        name: String,
        props: Map<String, Any?>,
        signalMatchConfig: PulseSignalMatchCondition,
        currentSdkName: PulseSdkName,
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

        if (signalMatchConfig.props.size != signalPropsFiltered.size) {
            return@PulseSignalMatcher false
        }

        signalPropsFiltered
            .all { signalProp ->
                val configProp = configPropsMap[signalProp.key]
                val signalValue = signalProp.value
                if (configProp == null || signalValue == null) {
                    signalValue == configProp
                } else {
                    signalValue.toString().matchesFromRegexCache(configProp)
                }
            }
    }
