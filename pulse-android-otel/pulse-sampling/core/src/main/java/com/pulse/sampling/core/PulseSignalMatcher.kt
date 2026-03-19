package com.pulse.sampling.core

import com.pulse.sampling.models.PulseProp
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import com.pulse.sampling.models.matchers.PulseSignalMatchCondition
import com.pulse.utils.matchesFromRegexCache
import io.opentelemetry.api.common.AttributeKey
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

        var isMatched = true

        val matchedConfigProps = mutableSetOf<PulseProp>()
        signalProps.forEach { signalPropKey, signalPropValue ->
            if (!isMatched) return@forEach

            val configProp =
                signalMatchConfig
                    .props
                    .firstOrNull { configProp ->
                        configProp.matches(signalPropKey, signalPropValue)
                    } ?: return@forEach

            matchedConfigProps += configProp

            val configPropValue = configProp.value

            isMatched =
                if (configPropValue == null || signalPropValue == null) {
                    signalPropValue == configPropValue
                } else {
                    signalPropValue.toString().matchesFromRegexCache(configPropValue)
                }
        }
        isMatched && matchedConfigProps.size == signalMatchConfig.props.size
    }

internal fun PulseProp.matches(
    signalKey: AttributeKey<*>,
    signalValue: Any,
): Boolean =
    signalKey.key.matchesFromRegexCache(this.name) &&
        (
            this.value == null ||
                signalValue
                    .toString()
                    .matchesFromRegexCache(this.value ?: error("value can't be null"))
        )
