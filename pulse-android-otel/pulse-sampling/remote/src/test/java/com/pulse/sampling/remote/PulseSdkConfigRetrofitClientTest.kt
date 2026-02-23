// test code
@file:Suppress("SuspendFunSwallowedCancellation")

package com.pulse.sampling.remote

import com.pulse.sampling.models.PulseDeviceAttributeName
import com.pulse.sampling.models.PulseFeatureName
import com.pulse.sampling.models.PulseSdkName
import com.pulse.sampling.models.PulseSignalFilterMode
import com.pulse.sampling.models.PulseSignalScope
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import retrofit2.HttpException
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PulseSdkConfigRetrofitClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var retrofitClient: PulseSdkConfigRetrofitClient
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var configUrl: String

    @field:TempDir
    lateinit var tempFolder: File

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val cache = Cache(tempFolder, 10 * 1024 * 1024)
        okHttpClient = OkHttpClient.Builder().apply { cache(cache) }.build()
        configUrl = mockWebServer.url("/").toString()
        retrofitClient =
            PulseSdkConfigRetrofitClient(
                url = configUrl,
                okhttpClient = okHttpClient,
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
        okHttpClient.cache?.close()
    }

    @Test
    fun `provide returns config when API response is successful`() =
        runTest {
            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(successResponseJson)
                    setHeader("Content-Type", "application/json")
                },
            )

            val config = retrofitClient.apiService.getConfig(configUrl, emptyMap())

            assertThat(config).isNotNull
            assertThat(config.version).isEqualTo(1)
            assertThat(config.sampling.default.sessionSampleRate).isEqualTo(0.5f)
            assertThat(config.sampling.rules).hasSize(5)
            assertThat(config.sampling.rules[0].name).isEqualTo(PulseDeviceAttributeName.OS_VERSION)
            assertThat(config.sampling.rules[0].value).isEqualTo("27")
            assertThat(config.sampling.rules[0].sessionSampleRate).isEqualTo(1.0f)
            assertThat(config.sampling.criticalEventPolicies).isNotNull
            assertThat(config.sampling.criticalEventPolicies!!.alwaysSend).hasSize(2)
            assertThat(
                config.sampling.criticalEventPolicies!!.alwaysSend[0],
            ).extracting({ it.name }, { it.sdks.toSet() }, { it.scopes.toSet() })
                .containsExactly(
                    "crash",
                    setOf(PulseSdkName.ANDROID_JAVA, PulseSdkName.ANDROID_RN, PulseSdkName.IOS_SWIFT, PulseSdkName.IOS_RN),
                    setOf(PulseSignalScope.LOGS, PulseSignalScope.TRACES, PulseSignalScope.METRICS, PulseSignalScope.BAGGAGE),
                )

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

            assertThat(okHttpClient.cache).isNotNull
            assertThat(
                okHttpClient.cache!!
                    .urls()
                    .asSequence()
                    .toList(),
            ).hasSize(1)
            assertThat(
                okHttpClient.cache!!
                    .urls()
                    .asSequence()
                    .first(),
            ).isEqualTo(configUrl)
        }

    @Test
    fun `provide throws exception when API response is error`() =
        runTest {
            val errorMessage = "Configuration not found for the given parameters"
            val expectedStatusCode = 404
            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(expectedStatusCode)
                    .setBody("""{"code":"CONFIG_NOT_FOUND","message":"$errorMessage"}""")
                    .setHeader("Content-Type", "application/json"),
            )

            val result =
                runCatching {
                    retrofitClient.apiService.getConfig(configUrl, emptyMap())
                }

            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull()
            assertThat(exception).isNotNull()
            assertThat(exception).isInstanceOf(HttpException::class.java)
            val httpException = (exception ?: error("exception is null")) as HttpException
            assertThat(httpException.code()).isEqualTo(expectedStatusCode)
            val responseBody = httpException.response()?.errorBody()?.string()
            assertThat(responseBody).isNotNull()
            assertThat(responseBody).contains(errorMessage)
        }

    @Test
    fun `handle unknown values in enum`() =
        runTest {
            val response =
                """
                {
                    "version": 1,
                        "description": "test config",
                        "sampling": {
                            "default": {
                                "sessionSampleRate": 0.5
                            },
                            "rules": [
                                {
                                    "name": "unknown_device_attr",
                                    "sdks": ["pulse_android_java", "unknown_sdk", "pulse_ios_rn"],
                                    "value": "test",
                                    "sessionSampleRate": 0.8
                                },
                                {
                                    "name": "os_version",
                                    "sdks": ["pulse_android_java"],
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
                                        "sdks": ["pulse_android_java", "future_sdk"]
                                    }
                                ]
                            }
                        },
                        "signals": {
                            "scheduleDurationMs": 5000,
                            "logsCollectorUrl": "http://localhost:4318/v1/traces",
                            "metricCollectorUrl": "http://localhost:4318/v1/traces",
                            "spanCollectorUrl": "http://localhost:4318/v1/traces",
                            "customEventCollectorUrl": "http://localhost:4318/v1/custom-event",
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
                    }
                """.trimIndent()

            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(response)
                    setHeader("Content-Type", "application/json")
                },
            )

            val config = retrofitClient.apiService.getConfig(configUrl, emptyMap())

            assertThat(config).isNotNull
            assertThat(config.sampling.rules[0].sdks)
                .containsExactlyInAnyOrder(PulseSdkName.ANDROID_JAVA, PulseSdkName.UNKNOWN, PulseSdkName.IOS_RN)
            assertThat(config.sampling.rules)
                .flatExtracting({ it.name })
                .containsExactlyInAnyOrder(PulseDeviceAttributeName.UNKNOWN, PulseDeviceAttributeName.OS_VERSION)

            assertThat(config.sampling.criticalEventPolicies).isNotNull

            assertThat(config.sampling.criticalEventPolicies!!.alwaysSend[0])
                .extracting({ it.scopes.toSet() }, { it.sdks.toSet() })
                .containsExactlyInAnyOrder(
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

    @Test
    fun `creation of retrofit client with file url should not crash`() =
        runTest {
            val configUrl = mockWebServer.url("/$CONFIG_REL_URL").toString()
            val retrofitClient =
                PulseSdkConfigRetrofitClient(
                    url = configUrl,
                    okhttpClient = okHttpClient,
                )
            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(successResponseJson)
                    setHeader("Content-Type", "application/json")
                },
            )

            val config =
                retrofitClient.apiService.getConfig(
                    fullFileUrl = configUrl,
                    headers = emptyMap(),
                )
            assertThat(config).isNotNull
        }

    private companion object {
        private const val CONFIG_REL_URL = "config.json"
        private val successResponseJson =
            """
            {
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
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
                                ],
                                "value": "27",
                                "sessionSampleRate": 1
                            },
                            {
                                "name": "app_version",
                                "sdks": [
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
                                ],
                                "value": "5.4.0",
                                "sessionSampleRate": 1
                            },
                            {
                                "name": "country",
                                "sdks": [
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
                                ],
                                "value": "IN",
                                "sessionSampleRate": 1
                            },
                            {
                                "name": "platform",
                                "sdks": [
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
                                ],
                                "value": "5.4.0",
                                "sessionSampleRate": 1
                            },
                            {
                                "name": "state",
                                "sdks": [
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
                                    ]
                                }
                            ]
                        },
                        "scheduleDurationMs": 5000,
                        "logsCollectorUrl": "http://localhost:4318/v1/traces",
                        "metricCollectorUrl": "http://localhost:4318/v1/traces",
                        "spanCollectorUrl": "http://localhost:4318/v1/traces",
                        "customEventCollectorUrl": "http://localhost:4318/v1/custom-event",
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
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
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
                                    "pulse_android_java",
                                    "pulse_android_rn",
                                    "pulse_ios_swift",
                                    "pulse_ios_rn"
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
                                        "pulse_android_java",
                                        "pulse_android_rn",
                                        "pulse_ios_swift",
                                        "pulse_ios_rn"
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
                                "pulse_android_java",
                                "pulse_android_rn",
                                "pulse_ios_swift",
                                "pulse_ios_rn"
                            ]
                        }
                    ]
                }
            """.trimIndent()
    }
}
