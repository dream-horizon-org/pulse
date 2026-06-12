// test code
@file:Suppress("SuspendFunSwallowedCancellation")

package com.pulse.android.remote

import com.pulse.utils.PulseNetworkingUtils
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException

class InteractionRetrofitClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: InteractionRetrofitClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client =
            InteractionRetrofitClient(
                url = mockWebServer.url("/").toString(),
                okHttpClient = PulseNetworkingUtils.okHttpClient,
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getInteractions returns successful response with interaction data`() =
        runTest {
            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response =
                client.apiService.getInteractions(
                    fullFileUrl = configUrl,
                    headers = emptyMap(),
                )

            assertThat(response)
                .isNotNull
                .hasSize(2)
                .doesNotContainNull()

            val firstInteraction = response[0]
            assertThat(firstInteraction).isNotNull
            assertThat(firstInteraction.id).isEqualTo(1)
            assertThat(firstInteraction.name).isEqualTo("TestInteractionId1")
            assertThat(firstInteraction.events).hasSize(2)
            assertThat(firstInteraction.globalBlacklistedEvents).isEmpty()
            assertThat(firstInteraction.uptimeLowerLimitInMs).isEqualTo(16)
            assertThat(firstInteraction.uptimeMidLimitInMs).isEqualTo(50)
            assertThat(firstInteraction.uptimeUpperLimitInMs).isEqualTo(100)
            assertThat(firstInteraction.thresholdInMs).isEqualTo(20000)
        }

    @Test
    fun `getInteractions throws exception when interaction not found`() =
        runTest {
            val errorMessage = "Interaction with name 'my-interaction' does not exist"
            val expectedStatusCode = 404
            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(expectedStatusCode)
                    .setBody("""{"code":"INTERACTION_NOT_FOUND","message":"$errorMessage"}""")
                    .setHeader("Content-Type", "application/json"),
            )

            val result =
                runCatching {
                    client.apiService.getInteractions(
                        fullFileUrl = configUrl,
                        headers = emptyMap(),
                    )
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
    fun `getInteractions handles empty data array`() =
        runTest {
            val emptyDataResponseJson = "[]"

            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(emptyDataResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response =
                client.apiService.getInteractions(
                    fullFileUrl = configUrl,
                    headers = emptyMap(),
                )

            assertThat(response).isNotNull.isEmpty()
        }

    @Test
    fun `newInstance creates new client with different URL`() =
        runTest {
            val newUrl = mockWebServer.url("/new-path/").toString()
            val newClient = client.newInstance(newUrl)

            val successResponseJson =
                """
                [
                    {
                        "id": 1,
                        "name": "Test",
                        "description": "test",
                        "uptimeLowerLimitInMs": 16,
                        "uptimeMidLimitInMs": 50,
                        "uptimeUpperLimitInMs": 100,
                        "thresholdInMs": 20000,
                        "events": [
                            {
                                "name": "event1",
                                "props": [],
                                "isBlacklisted": false
                            }
                        ],
                        "globalBlacklistedEvents": []
                    }
                ]
                """.trimIndent()

            val configUrl = mockWebServer.url(CONFIG_REL_URL).toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response =
                newClient.apiService.getInteractions(
                    fullFileUrl = configUrl,
                    headers = emptyMap(),
                )

            assertThat(response).isNotNull.hasSize(1)
            assertThat(response[0].name).isEqualTo("Test")
        }

    @Test
    fun `creation of retrofit client with file url should not crash`() =
        runTest {
            val configUrl = mockWebServer.url("/$CONFIG_REL_URL").toString()
            val client =
                InteractionRetrofitClient(
                    url = configUrl,
                    okHttpClient = PulseNetworkingUtils.okHttpClient,
                )
            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(successResponseJson)
                    setHeader("Content-Type", "application/json")
                },
            )

            val response =
                client.apiService.getInteractions(
                    fullFileUrl = configUrl,
                    headers = emptyMap(),
                )
            assertThat(response).isNotNull
        }

    @Test
    fun `headers gets appended when passed in the api`() =
        runTest {
            val configUrl = mockWebServer.url("/$CONFIG_REL_URL").toString()
            val client =
                InteractionRetrofitClient(
                    url = configUrl,
                    okHttpClient = PulseNetworkingUtils.okHttpClient,
                )
            mockWebServer.enqueue(
                MockResponse().apply {
                    setResponseCode(200)
                    setBody(successResponseJson)
                    setHeader("Content-Type", "application/json")
                },
            )

            client.apiService.getInteractions(
                fullFileUrl = configUrl,
                headers = mapOf("headerKey" to "headerValue"),
            )
            val request = mockWebServer.takeRequest()
            assertThat(request.headers).contains("headerKey" to "headerValue")
        }

    private companion object {
        private const val CONFIG_REL_URL = "config.json"
        private val successResponseJson =
            """
            [
                {
                    "id": 1,
                        "name": "TestInteractionId1",
                        "description": "kijikn knmlmlm",
                        "uptimeLowerLimitInMs": 16,
                        "uptimeMidLimitInMs": 50,
                        "uptimeUpperLimitInMs": 100,
                        "thresholdInMs": 20000,
                        "events": [
                            {
                                "name": "mknjk",
                                "props": [],
                                "isBlacklisted": false
                            },
                            {
                                "name": "knn knn k,",
                                "props": [],
                                "isBlacklisted": false
                            }
                        ],
                        "globalBlacklistedEvents": []
                    },
                    {
                        "id": 2,
                        "name": "TestInteraction",
                        "description": "interaction",
                        "uptimeLowerLimitInMs": 16,
                        "uptimeMidLimitInMs": 50,
                        "uptimeUpperLimitInMs": 100,
                        "thresholdInMs": 20000,
                        "events": [
                            {
                                "name": "Start Event",
                                "props": [
                                    {
                                        "name": "hello",
                                        "value": "world",
                                        "operator": "EQUALS"
                                    }
                                ],
                                "isBlacklisted": false
                            },
                            {
                                "name": "Event T1",
                                "props": [
                                    {
                                        "name": "t1",
                                        "value": "t1value",
                                        "operator": "EQUALS"
                                    }
                                ],
                                "isBlacklisted": false
                            },
                            {
                                "name": "loCal Blacklist",
                                "props": [
                                    {
                                        "name": "local",
                                        "value": "local",
                                        "operator": "EQUALS"
                                    }
                                ],
                                "isBlacklisted": true
                            },
                            {
                                "name": "End Event",
                                "props": [
                                    {
                                        "name": "abhishej",
                                        "value": "test",
                                        "operator": "EQUALS"
                                    }
                                ],
                                "isBlacklisted": false
                            }
                        ],
                        "globalBlacklistedEvents": [
                            {
                                "name": "Gloabal Blacklist Event",
                                "props": [
                                    {
                                        "name": "global",
                                        "value": "global",
                                        "operator": "EQUALS"
                                    }
                                ],
                                "isBlacklisted": true
                            }
                        ]
                    }
                ]
            """.trimIndent()
    }
}
