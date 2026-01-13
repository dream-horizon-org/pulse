package com.pulse.sampling.core

import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeProp
import com.pulse.sampling.models.PulseSdkConfigFakeUtils.createFakeSignalMatchCondition
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PulseSignalsAttrSamplerTest {
    private val signalMatcher: PulseSignalMatcher = PulseSignalsAttrMatcher()

    @Test
    fun `matches returns true when all conditions are met`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1", "key2" to "value2")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isTrue
    }

    @Test
    fun `matches returns false when sdk name is not present`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1", "key2" to "value2")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = emptySet(),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns false when scope is not present`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1", "key2" to "value2")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = emptySet(),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns false when name does not match regex`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1", "key2" to "value2")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "other_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns false when props size mismatch`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns false when prop value mismatch`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value1", "key2" to "value3")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1"), createFakeProp("key2", "value2")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns true with regex for name`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal_123"
        val signalProps = mapOf("key1" to "value1")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal_.*",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value1")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isTrue
    }

    @Test
    fun `matches returns true with regex for props`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value_123")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value_.*")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isTrue
    }

    @Test
    fun `matches returns true when props value are null`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value_1", "key2" to null)
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value_1"), createFakeProp("key2", null)),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isTrue
    }

    @Test
    fun `matches returns false when props value is null but config is empty`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value_1", "key2" to null)
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value_1"), createFakeProp("key2", "")),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }

    @Test
    fun `matches returns false when prop value is empty but config is null`() {
        val signalScope = PulseSignalScope.TRACES
        val signalName = "test_signal"
        val signalProps = mapOf("key1" to "value_1", "key2" to "")
        val signalMatchConfig =
            createFakeSignalMatchCondition(
                name = "test_signal",
                sdks = setOf(PulseSdkName.CURRENT_SDK_NAME),
                scopes = setOf(PulseSignalScope.TRACES),
                props = setOf(createFakeProp("key1", "value_1"), createFakeProp("key2", null)),
            )

        val result = signalMatcher.matches(signalScope, signalName, signalProps, signalMatchConfig)

        assertThat(result).isFalse
    }
}
