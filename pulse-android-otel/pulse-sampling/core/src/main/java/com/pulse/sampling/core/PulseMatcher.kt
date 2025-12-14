package com.pulse.sampling.core

import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import java.util.concurrent.ConcurrentHashMap

public fun interface PulseMatcher {
    public fun matches(
        scope: PulseSignalScope,
        name: String,
        props: Map<String, Any?>,
        signalMatchConfig: PulseSignalMatchCondition,
    ): Boolean
}

internal val regexCache = ConcurrentHashMap<String, Regex>()

@Suppress("FunctionName")
internal fun PulseSignalsAttrMatcher() = PulseMatcher { signalScope, signalName, signalProps, signalMatchConfig ->
    if (
        !(
            signalMatchConfig.sdks.contains(PulseSdkName.CURRENT_SDK_NAME) &&
                signalMatchConfig.scopes.contains(signalScope) &&
                signalName.matchesFromRegexCache(signalMatchConfig.name)
            )
    ) {
        return@PulseMatcher false
    }

    val configPropsMap = signalMatchConfig.props.associate { it.name to it.value }
    val signalPropsFiltered = signalProps.filter { it.key in configPropsMap.keys }

    if (signalMatchConfig.props.size != signalPropsFiltered.size) {
        return@PulseMatcher false
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

internal fun String.matchesFromRegexCache(regexStr: String): Boolean {
    val regex = regexCache.computeIfAbsent(regexStr) { regexStr.toRegex() }
    return this.matches(regex)
}
