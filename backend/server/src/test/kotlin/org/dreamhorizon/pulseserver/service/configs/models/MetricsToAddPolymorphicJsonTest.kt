package org.dreamhorizon.pulseserver.service.configs.models

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetricsToAddPolymorphicJsonTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun shouldDeserializeMetricsToAddWithCounterAndSum() {
        val json =
            """
            {
                "name": "n",
                "target": { "type": "name" },
                "condition": {
                    "name": ".*",
                    "props": [],
                    "scopes": ["traces"],
                    "sdks": ["pulse_android_java"]
                },
                "type": { "type": "counter" },
                "attributesToPick": []
            }
            """.trimIndent()

        val entry = mapper.readValue<MetricsToAddEntry>(json)
        assertThat(entry.name).isEqualTo("n")
        assertThat(entry.target).isInstanceOf(MetricsToAddTarget.Name::class.java)
        assertThat((entry.target as MetricsToAddTarget.Name).type).isEqualTo("name")
        assertThat(entry.type).isInstanceOf(MetricsType.Counter::class.java)

        val sumJson =
            """
            {
                "name": "s",
                "target": { "type": "name" },
                "condition": {
                    "name": "x",
                    "props": [],
                    "scopes": ["metrics"],
                    "sdks": []
                },
                "type": { "type": "sum", "isFraction": false, "isMonotonic": true },
                "attributesToPick": []
            }
            """.trimIndent()
        val sumEntry = mapper.readValue<MetricsToAddEntry>(sumJson)
        assertThat(sumEntry.type).isInstanceOf(MetricsType.Sum::class.java)
        val sum = sumEntry.type as MetricsType.Sum
        assertThat(sum.isFraction).isFalse()
        assertThat(sum.isMonotonic).isTrue()
    }

    @Test
    fun shouldDeserializeSignalsToSample() {
        val json =
            """
            {
                "condition": {
                    "name": "evt",
                    "props": [],
                    "scopes": ["traces"],
                    "sdks": ["pulse_android_java"]
                },
                "sampleRate": 0.25
            }
            """.trimIndent()
        val e = mapper.readValue<SignalsToSampleEntry>(json)
        assertThat(e.sampleRate).isEqualTo(0.25)
        assertThat(e.condition?.name).isEqualTo("evt")
    }

    @Test
    fun shouldRoundTripSamplingConfigWithSignalsToSample() {
        val cfg =
            SamplingConfig
                .builder()
                .signalsToSample(
                    listOf(
                        SignalsToSampleEntry(
                            EventFilter.builder().name("a").build(),
                            0.5,
                        ),
                    ),
                ).build()
        val s = mapper.writeValueAsString(cfg)
        val back = mapper.readValue<SamplingConfig>(s)
        assertThat(back.signalsToSample).hasSize(1)
        assertThat(back.signalsToSample!![0].sampleRate).isEqualTo(0.5)
    }
}
