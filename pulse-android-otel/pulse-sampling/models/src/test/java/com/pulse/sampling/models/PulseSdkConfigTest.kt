package com.pulse.sampling.models

import com.pulse.utils.PulseSerialisationUtils
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PulseSdkConfigTest {
    @Nested
    inner class NonStrictConfig {
        private val json = PulseSerialisationUtils.createJsonConfig(isStrict = false)

        @Test
        fun `empty JSON does not crash`() {
            assertThatCode { json.decodeFromString<PulseSdkConfig>("{}") }.doesNotThrowAnyException()
        }
    }

    @Nested
    inner class StrictConfig {
        private val json = PulseSerialisationUtils.createJsonConfig(isStrict = true)

        @Test
        fun `empty JSON does not crash when all fields have defaults`() {
            assertThatCode { json.decodeFromString<PulseSdkConfig>("{}") }.doesNotThrowAnyException()
        }
    }
}
