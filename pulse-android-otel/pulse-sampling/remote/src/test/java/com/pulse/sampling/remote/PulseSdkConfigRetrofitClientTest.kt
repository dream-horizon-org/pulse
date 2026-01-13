package com.pulse.sampling.remote

import com.pulse.otel.utils.models.PulseApiResponse
import com.pulse.sampling.models.PulseDeviceAttributeName
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PulseSdkConfigRetrofitClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var retrofitClient: PulseSdkConfigRetrofitClient

    @field:TempDir
    lateinit var tempFolder: File

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        retrofitClient = PulseSdkConfigRetrofitClient(mockWebServer.url("/").toString(), tempFolder)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `provide returns config when API response is successful`() =
        runTest {
            val successResponseJson =
                """
                {
                    "data": {
                        "version": 1,
                        "description": "this is  test config",
                        "sampling": {
                            "default": {
                                "sessionSampleRate": 0.5
                            },
                            "rules": [
                                {
                                    "name": "os_version",
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ],
                                    "value": "27",
                                    "sessionSampleRate": 1
                                },
                                {
                                    "name": "app_version",
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ],
                                    "value": "5.4.0",
                                    "sessionSampleRate": 1
                                },
                                {
                                    "name": "country",
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ],
                                    "value": "IN",
                                    "sessionSampleRate": 1
                                },
                                {
                                    "name": "platform",
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ],
                                    "value": "5.4.0",
                                    "sessionSampleRate": 1
                                },
                                {
                                    "name": "state",
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ],
                                    "value": "MH",
                                    "sessionSampleRate": 1
                                }
                            ],
                            "criticalEventPolicies": {
                                "alwaysSend": [
                                    {
                                        "name": "crash",
                                        "props": [
                                            {
                                                "name": "severity",
                                                "value": "critical"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    },
                                    {
                                        "name": "payment_error",
                                        "props": [
                                            {
                                                "name": "error_type",
                                                "value": "payment.*"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    }
                                ]
                            },
                            "criticalSessionPolicies": {
                                "alwaysSend": [
                                    {
                                        "name": "crash",
                                        "props": [
                                            {
                                                "name": "severity",
                                                "value": "critical"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    },
                                    {
                                        "name": "payment_error",
                                        "props": [
                                            {
                                                "name": "error_type",
                                                "value": "payment.*"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    }
                                ]
                            }
                        },
                        "signals": {
                            "filters": {
                                "mode": "blacklist",
                                "values": [
                                    {
                                        "name": "sensitive_event",
                                        "props": [
                                            {
                                                "name": "contains_pii",
                                                "value": "true"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    }
                                ]
                            },
                            "scheduleDurationMs": 5000,
                            "logsCollectorUrl": "http://localhost:4318/v1/traces",
                            "metricCollectorUrl": "http://localhost:4318/v1/traces",
                            "spanCollectorUrl": "http://localhost:4318/v1/traces",
                            "attributesToDrop": [
                                {
                                    "name": "credit_card",
                                    "props": [
                                        {
                                            "name": "severity",
                                            "value": "critical"
                                        }
                                    ],
                                    "scopes": [
                                        "logs",
                                        "traces",
                                        "metrics",
                                        "baggage"
                                    ],
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ]
                                },
                                {
                                    "name": "password",
                                    "props": [
                                        {
                                            "name": "severity",
                                            "value": "critical"
                                        }
                                    ],
                                    "scopes": [
                                        "logs",
                                        "traces",
                                        "metrics",
                                        "baggage"
                                    ],
                                    "sdks": [
                                        "android_java",
                                        "android_rn",
                                        "ios_native",
                                        "ios_rn"
                                    ]
                                }
                            ],
                            "attributesToAdd": [
                                {
                                    "values": [
                                        {
                                            "name": "NewAddedKeyName",
                                            "value": "NewAddedValueOfThatKey",
                                            "type": "string"
                                        }
                                    ],
                                    "condition": {
                                        "name": "password",
                                        "props": [
                                            {
                                                "name": "severity",
                                                "value": "critical"
                                            }
                                        ],
                                        "scopes": [
                                            "logs",
                                            "traces",
                                            "metrics",
                                            "baggage"
                                        ],
                                        "sdks": [
                                            "android_java",
                                            "android_rn",
                                            "ios_native",
                                            "ios_rn"
                                        ]
                                    }
                                }
                            ]
                        },
                        "interaction": {
                            "collectorUrl": "http://localhost:4318/v1/interactions",
                            "configUrl": "http://localhost:8080/v1/configs/latest-version",
                            "beforeInitQueueSize": 100
                        },
                        "features": [
                            {
                                "featureName": "java_crash",
                                "sessionSampleRate": 0.8,
                                "sdks": [
                                    "android_java",
                                    "android_rn",
                                    "ios_native",
                                    "ios_rn"
                                ]
                            }
                        ]
                    },
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(successResponseJson)
                    setHeader("Content-Type", "application/json")
                },
            )

            val configApiResponse: PulseApiResponse<PulseSdkConfig> = retrofitClient.apiService.getConfig()

            val config = configApiResponse.data

            assertThat(config).isNotNull
            assertThat(config!!.version).isEqualTo(1)
            assertThat(config.sampling.default.sessionSampleRate).isEqualTo(0.5f)
            assertThat(config.sampling.rules).hasSize(5)
            assertThat(config.sampling.rules[0].name).isEqualTo(PulseDeviceAttributeName.OS_VERSION)
            assertThat(config.sampling.rules[0].value).isEqualTo("27")
            assertThat(config.sampling.rules[0].sessionSampleRate).isEqualTo(1.0f)
            assertThat(config.sampling.criticalEventPolicies).isNotNull
            assertThat(config.sampling.criticalEventPolicies!!.alwaysSend).hasSize(2)
            assertThat(
                config.sampling.criticalEventPolicies!!
                    .alwaysSend[0]
                    .name,
            ).isEqualTo("crash")
            assertThat(
                config.sampling.criticalEventPolicies!!
                    .alwaysSend[0]
                    .props,
            ).hasSize(1)
            assertThat(
                config.sampling.criticalEventPolicies!!
                    .alwaysSend[0]
                    .props
                    .first()
                    .name,
            ).isEqualTo("severity")
            assertThat(
                config.sampling.criticalEventPolicies!!
                    .alwaysSend[0]
                    .props
                    .first()
                    .value,
            ).isEqualTo("critical")

            assertThat(config.signals.scheduleDurationMs).isEqualTo(5000L)
            assertThat(config.signals.spanCollectorUrl).isEqualTo("http://localhost:4318/v1/traces")
            assertThat(config.signals.attributesToDrop).hasSize(2)
            assertThat(config.signals.filters.mode).isEqualTo(PulseSignalFilterMode.BLACKLIST)
            assertThat(config.signals.filters.values).hasSize(1)
            assertThat(
                config.signals.filters.values[0]
                    .name,
            ).isEqualTo("sensitive_event")

            assertThat(config.interaction.collectorUrl).isEqualTo("http://localhost:4318/v1/interactions")
            assertThat(config.interaction.configUrl).isEqualTo("http://localhost:8080/v1/configs/latest-version")
            assertThat(config.interaction.beforeInitQueueSize).isEqualTo(100)

            assertThat(config.features).hasSize(1)
            assertThat(config.features[0].featureName).isEqualTo(PulseFeatureName.JAVA_CRASH)
            assertThat(config.features[0].sessionSampleRate).isEqualTo(0.8f)
        }

    @Test
    fun `provide returns null when API response contains error`() =
        runTest {
            val errorResponseJson =
                """
                {
                    "data": null,
                    "error": {
                        "code": "CONFIG_NOT_FOUND",
                        "message": "Configuration not found for the given parameters"
                    }
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(errorResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val configApiResponse = retrofitClient.apiService.getConfig()

            assertThat(configApiResponse.data).isNull()
            assertThat(configApiResponse.error).isNotNull
        }

    @Test
    fun `handle unknown values in enum`() =
        runTest {
            val response =
                """
                {
                    "data": {
                        "version": 1,
                        "description": "test config",
                        "sampling": {
                            "default": {
                                "sessionSampleRate": 0.5
                            },
                            "rules": [
                                {
                                    "name": "unknown_device_attr",
                                    "sdks": ["android_java", "unknown_sdk", "ios_rn"],
                                    "value": "test",
                                    "sessionSampleRate": 0.8
                                },
                                {
                                    "name": "os_version",
                                    "sdks": ["android_java"],
                                    "value": "value",
                                    "sessionSampleRate": 0.9
                                }
                            ],
                            "criticalEventPolicies": {
                                "alwaysSend": [
                                    {
                                        "name": "test_event",
                                        "props": [],
                                        "scopes": ["logs", "unknown_scope", "traces"],
                                        "sdks": ["android_java", "future_sdk"]
                                    }
                                ]
                            }
                        },
                        "signals": {
                            "scheduleDurationMs": 5000,
                            "logsCollectorUrl": "http://localhost:4318/v1/traces",
                            "metricCollectorUrl": "http://localhost:4318/v1/traces",
                            "spanCollectorUrl": "http://localhost:4318/v1/traces",
                            "attributesToDrop": [],
                            "attributesToAdd": [],
                            "filters": {
                                "mode": "blacklist",
                                "values": []
                            }
                        },
                        "interaction": {
                            "collectorUrl": "http://localhost:4318/v1/interactions",
                            "configUrl": "http://localhost:8080/v1/configs/latest-version",
                            "beforeInitQueueSize": 100
                        },
                        "features": []
                    },
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(response)
                    setHeader("Content-Type", "application/json")
                },
            )

            val configApiResponse: PulseApiResponse<PulseSdkConfig> = retrofitClient.apiService.getConfig()

            val config = configApiResponse.data

            assertThat(config).isNotNull
            assertThat(config!!.sampling.rules[0].sdks)
                .containsExactly(PulseSdkName.ANDROID_JAVA, PulseSdkName.UNKNOWN, PulseSdkName.IOS_RN)
            assertThat(config.sampling.rules)
                .flatExtracting({ it.name })
                .containsExactly(PulseDeviceAttributeName.UNKNOWN, PulseDeviceAttributeName.OS_VERSION)

            assertThat(config.sampling.criticalEventPolicies).isNotNull

            assertThat(config.sampling.criticalEventPolicies!!.alwaysSend[0])
                .extracting({ it.scopes }, { it.sdks })
                .containsExactly(
                    setOf(
                        PulseSignalScope.LOGS,
                        PulseSignalScope.UNKNOWN,
                        PulseSignalScope.TRACES,
                    ),
                    setOf(
                        PulseSdkName.ANDROID_JAVA,
                        PulseSdkName.UNKNOWN,
                    ),
                )
        }
}
