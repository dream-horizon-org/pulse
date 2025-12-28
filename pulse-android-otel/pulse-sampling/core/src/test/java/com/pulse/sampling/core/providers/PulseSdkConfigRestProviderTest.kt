package com.pulse.sampling.core.providers

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class PulseSdkConfigRestProviderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: PulseSdkConfigRestProvider

    @field:TempDir
    lateinit var tempFolder: File

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val cacheDir = File(tempFolder, "cache")
        cacheDir.mkdirs()
        provider =
            PulseSdkConfigRestProvider(cacheDir) {
                mockWebServer.url("/").toString()
            }
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `when API returns successful response, should return PulseSdkConfig`() =
        runTest {
            val successResponseJson =
                """
                {
                    "data": {
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
                            "attributesToDrop": [],
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
                    },
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val result = provider.provide()

            assertThat(result).isNotNull
            assertThat(result!!.version).isEqualTo(1)
            assertThat(result.description).isEqualTo("test config")
            assertThat(result.sampling.default.sessionSampleRate).isEqualTo(0.5f)
        }

    @Test
    fun `when API returns successful response with error in JSON, should return null`() =
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

            val result = provider.provide()

            assertThat(result).isNull()
        }

    @Test
    fun `when API returns failed response like 404, should return null`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("{invalid json}")
                    .setHeader("Content-Type", "application/json"),
            )

            val result = provider.provide()

            assertThat(result).isNull()
        }

    @Test
    fun `when url is invalid should return null`() =
        runTest {
            val cacheDir = File(tempFolder, "cache2")
            cacheDir.mkdirs()
            val invalidUrlProvider =
                PulseSdkConfigRestProvider(cacheDir) {
                    "http://127.0.0.1:1/invalid/"
                }

            val result = invalidUrlProvider.provide()

            assertThat(result).isNull()
        }
}
