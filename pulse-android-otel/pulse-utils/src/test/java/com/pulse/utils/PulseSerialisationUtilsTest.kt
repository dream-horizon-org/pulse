package com.pulse.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PulseSerialisationUtilsTest {
    @Serializable
    private data class ModelWithDefaults(
        val nonNullField: String = "default",
        val nullableField: String? = null,
        val flag: Flag? = null,
    )

    @Serializable
    private data class ModelWithRequiredFields(
        val nonNullField: String,
        val nullableField: String?,
    )

    enum class Flag {
        A,
        B,
    }

    @Nested
    inner class NonStrictConfig {
        private val json = PulseSerialisationUtils.createJsonConfig(isStrict = false)

        @Test
        fun `empty JSON does not crash`() {
            assertThatCode { json.decodeFromString<ModelWithDefaults>("{}") }.doesNotThrowAnyException()
        }

        @Test
        fun `non-null field missing in JSON uses default value`() {
            val result = json.decodeFromString<ModelWithDefaults>("{}")

            assertThat(result.nonNullField).isEqualTo("default")
        }

        @Test
        fun `nullable field missing in JSON deserialises to null`() {
            val result = json.decodeFromString<ModelWithDefaults>("{}")

            assertThat(result.nullableField).isNull()
        }

        @Test
        fun `wrong type field present in nullable field`() {
            val result =
                json.decodeFromString<ModelWithDefaults>(
                    """
                    {
                      "nonNullField" : null
                    }
                    """.trimIndent(),
                )

            assertThat(result.nonNullField).isEqualTo("default")
        }

        @Test
        fun `null value with for nullable enum field`() {
            assertThat(
                json
                    .decodeFromString<ModelWithDefaults>(
                        """
                        {
                          "flag" : "unknown"
                        }
                        """.trimIndent(),
                    ).flag,
            ).isNull()
        }
    }

    @Nested
    inner class StrictConfig {
        private val json = PulseSerialisationUtils.createJsonConfig(isStrict = true)

        @Test
        fun `empty JSON does not crash when all fields have defaults`() {
            assertThatCode { json.decodeFromString<ModelWithDefaults>("{}") }.doesNotThrowAnyException()
        }

        @Test
        fun `non-null required field missing in JSON throws SerializationException`() {
            assertThatThrownBy { json.decodeFromString<ModelWithRequiredFields>("{}") }
                .isInstanceOf(SerializationException::class.java)
        }

        @Test
        fun `nullable required field missing in JSON throws SerializationException`() {
            assertThatThrownBy { json.decodeFromString<ModelWithRequiredFields>("{}") }
                .isInstanceOf(SerializationException::class.java)
        }

        @Test
        fun `wrong type field present in nullable field`() {
            assertThatThrownBy {
                json.decodeFromString<ModelWithDefaults>(
                    """
                    {
                      "nullableField" : 9
                    }
                    """.trimIndent(),
                )
            }.isInstanceOf(SerializationException::class.java)
        }

        @Test
        fun `wrong type field present in non nullable field`() {
            assertThatThrownBy {
                json.decodeFromString<ModelWithRequiredFields>(
                    """
                    {
                      "nonNullField" : 9
                    }
                    """.trimIndent(),
                )
            }.isInstanceOf(SerializationException::class.java)
        }

        @Test
        fun `null value with for nullable enum field`() {
            assertThatThrownBy {
                json.decodeFromString<ModelWithDefaults>(
                    """
                    {
                      "flag" : "unknown"
                    }
                    """.trimIndent(),
                )
            }.isInstanceOf(SerializationException::class.java)
        }
    }
}
