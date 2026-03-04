package com.pulse.sampling.core.providers

import com.pulse.otel.utils.PulseNetworkingUtils
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class PulseSdkConfigRestProviderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: PulseSdkConfigRestProvider
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var configUrl: String
    private val headerPair = "headerKey" to "headerValue"

    @field:TempDir
    lateinit var tempFolder: File

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val cacheDir = File(tempFolder, "cache")
        cacheDir.mkdirs()
        val cache = Cache(cacheDir, 10 * 1024 * 1024)
        okHttpClient =
            PulseNetworkingUtils.okHttpClient
                .newBuilder()
                .apply {
                    cache(cache)
                }.build()
        configUrl = mockWebServer.url("/").toString()
        provider =
            PulseSdkConfigRestProvider(
                cacheDir = cacheDir,
                okHttpClient = okHttpClient,
                headers = mapOf(headerPair),
            ) {
                configUrl
            }
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Nested
    inner class `with successful response` {
        val successResponseJson =
            """
            {
                "version": 1,
                    "description": "test config",
                    "sampling": {
                        "default": {
                            "sessionSampleRate": 0.5
                        },
                        "rules": []
                    },
                    "signals": {
                        "scheduleDurationMs": 5000,
                        "logsCollectorUrl": "http://localhost:4318/v1/logs",
                        "metricCollectorUrl": "http://localhost:4318/v1/metrics",
                        "spanCollectorUrl": "http://localhost:4318/v1/traces",
                        "customEventCollectorUrl": "http://localhost:4318/v1/traces",
                        "attributesToDrop": [],
                        "attributesToAdd": [],
                        "filters": {
                            "mode": "whitelist",
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

        @Test
        fun `when API returns successful response, should return PulseSdkConfig`() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(successResponseJson)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=60"),
                )

                val result = provider.provide()

                assertThat(result).isNotNull
                assertThat(result!!.version).isEqualTo(1)
                assertThat(result.description).isEqualTo("test config")
                assertThat(result.sampling.default.sessionSampleRate).isEqualTo(0.5f)
            }

        @Test
        fun `with cache control max-age 60, use the cache`() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(successResponseJson)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=60"),
                )

                provider.provide()
                provider.provide()

                assertThat(okHttpClient.cache).isNotNull
                assertThat(okHttpClient.cache!!.requestCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.networkCount()).isEqualTo(1)
                assertThat(okHttpClient.cache!!.hitCount()).isEqualTo(1)
            }

        @Test
        fun `with no store, hit count is zero`() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(successResponseJson)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "no-store"),
                )

                provider.provide()
                provider.provide()

                assertThat(okHttpClient.cache).isNotNull
                assertThat(okHttpClient.cache!!.requestCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.networkCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.hitCount()).isEqualTo(0)
            }

        @Test
        fun `headers gets appended when passed in the api`() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(successResponseJson)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "no-store"),
                )
                provider.provide()

                val request = mockWebServer.takeRequest()
                assertThat(request.headers).contains(headerPair)
            }
    }

    @Nested
    inner class `with invalid response` {
        private val invalidResponse = "{invalid json}"

        @Test
        fun `when response code is 404, response return null`() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody(invalidResponse)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=60"),
                )

                val result = provider.provide()
                assertThat(result).isNull()
            }

        @Test
        fun `when response code is 404 hit is not used `() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody(invalidResponse)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=60"),
                )

                provider.provide()
                provider.provide()

                assertThat(okHttpClient.cache).isNotNull
                assertThat(okHttpClient.cache!!.requestCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.networkCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.hitCount()).isEqualTo(0)
            }

        @Test
        fun `when response code is 200 hit is not used `() =
            runTest {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(invalidResponse)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Cache-Control", "max-age=60"),
                )

                provider.provide()
                provider.provide()

                assertThat(okHttpClient.cache).isNotNull
                assertThat(okHttpClient.cache!!.requestCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.networkCount()).isEqualTo(2)
                assertThat(okHttpClient.cache!!.hitCount()).isEqualTo(0)
            }
    }

    @Test
    fun `when API returns successful response with error in JSON, should return null`() =
        runTest {
            val errorResponseJson =
                """
                {
                    "code": "CONFIG_NOT_FOUND",
                    "message": "Configuration not found for the given parameters"
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(errorResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val result = provider.provide()
            provider.provide()

            assertThat(result).isNull()

            assertThat(okHttpClient.cache).isNotNull
            assertThat(okHttpClient.cache!!.requestCount()).isEqualTo(2)
            assertThat(okHttpClient.cache!!.networkCount()).isEqualTo(2)
            assertThat(okHttpClient.cache!!.hitCount()).isEqualTo(0)
        }

    @Test
    fun `when url is invalid should return null`() =
        runTest {
            val cacheDir = File(tempFolder, "cache2")
            cacheDir.mkdirs()
            val invalidUrlProvider =
                PulseSdkConfigRestProvider(
                    cacheDir = cacheDir,
                    okHttpClient = PulseNetworkingUtils.okHttpClient,
                ) {
                    "http://127.0.0.1:1/invalid/"
                }

            val result = invalidUrlProvider.provide()

            assertThat(result).isNull()
        }
}
