package com.pulse.android.remote

import com.pulse.android.remote.models.InteractionConfig
import com.pulse.otel.utils.models.PulseApiResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InteractionRetrofitClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: InteractionRetrofitClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = InteractionRetrofitClient(mockWebServer.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getInteractions returns successful response with interaction data`() =
        runTest {
            val successResponseJson =
                """
                {
                    "data": [
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
                    ],
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response: PulseApiResponse<List<InteractionConfig>> = client.apiService.getInteractions()

            assertThat(response)
                .isNotNull
            assertThat(response.data)
                .isNotNull
                .hasSize(2)
                .doesNotContainNull()
            assertThat(response.error).isNull()

            val firstInteraction = response.data!![0]
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
    fun `getInteractions returns error response when interaction not found`() =
        runTest {
            val errorResponseJson =
                """
                {
                    "data": null,
                    "error": {
                        "code": "INTERACTION_NOT_FOUND",
                        "message": "Interaction with name 'my-interaction' does not exist"
                    }
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(errorResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response: PulseApiResponse<List<InteractionConfig>> = client.apiService.getInteractions()

            assertThat(response)
                .isNotNull
                .extracting { it.data }
                .isNull()
            assertThat(response.error)
                .isNotNull
                .extracting { it!!.code }
                .isEqualTo("INTERACTION_NOT_FOUND")
            assertThat(response.error!!.message)
                .isEqualTo("Interaction with name 'my-interaction' does not exist")
        }

    @Test
    fun `getInteractions handles empty data array`() =
        runTest {
            val emptyDataResponseJson =
                """
                {
                    "data": [],
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(emptyDataResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response: PulseApiResponse<List<InteractionConfig>> = client.apiService.getInteractions()

            assertThat(response).isNotNull
            assertThat(response.data)
                .isNotNull
                .isEmpty()
            assertThat(response.error).isNull()
        }

    @Test
    fun `newInstance creates new client with different URL`() =
        runTest {
            val newUrl = mockWebServer.url("/new-path/").toString()
            val newClient = client.newInstance(newUrl)

            val successResponseJson =
                """
                {
                    "data": [
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
                    ],
                    "error": null
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(successResponseJson)
                    .setHeader("Content-Type", "application/json"),
            )

            val response: PulseApiResponse<List<InteractionConfig>> = newClient.apiService.getInteractions()

            assertThat(response).isNotNull
            assertThat(response.data)
                .isNotNull
                .hasSize(1)
            assertThat(response.data!![0].name).isEqualTo("Test")
        }
}
