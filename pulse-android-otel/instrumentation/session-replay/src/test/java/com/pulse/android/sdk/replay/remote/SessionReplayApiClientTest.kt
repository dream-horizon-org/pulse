package com.pulse.android.sdk.replay.remote

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionReplayApiClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: SessionReplayApiClient

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = SessionReplayApiClient(baseUrl = mockWebServer.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `sendBatch returns success when server returns 200`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}"),
        )

        val envelope = """{"event":"snapshot","project_id":"p1","user_id":"u1","properties":{}}"""
        val result = client.sendBatch(envelope)

        assertThat(result.isSuccess).isTrue()
        val request = mockWebServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/s/")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
        assertThat(request.body.readUtf8()).isEqualTo("[$envelope]")
    }

    @Test
    fun `sendBatch returns failure when server returns 500`() {
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(500).setBody("Internal error"),
            )
        }

        val result = client.sendBatch("""{"event":"snapshot"}""")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("500")
    }

    @Test
    fun `sendBatch returns failure when server returns 404`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(404).setBody("Not found"),
        )

        val result = client.sendBatch("""{"event":"snapshot"}""")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("404")
    }

    @Test
    fun `sendBatch retries on 500 then succeeds on 200`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal error"))
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{}"),
        )

        val envelope = """{"event":"snapshot"}"""
        val result = client.sendBatch(envelope)

        assertThat(result.isSuccess).isTrue()
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `sendBatch does not retry on 400`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("Bad request"),
        )

        val result = client.sendBatch("""{"event":"snapshot"}""")

        assertThat(result.isFailure).isTrue()
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `sendBatch wraps single envelope in array`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{}"),
        )

        val envelope = """{"event":"snapshot"}"""
        client.sendBatch(envelope)

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).startsWith("[")
        assertThat(body).endsWith("]")
    }

    @Test
    fun `sendBatch does not double-wrap already batched array`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{}"),
        )

        val batch = """[{"event":"a"},{"event":"b"}]"""
        client.sendBatch(batch)

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).isEqualTo(batch)
    }

    @Test
    fun `sendBatch with maxRetries 0 makes single attempt`() {
        val clientWithNoRetries =
            SessionReplayApiClient(
                baseUrl = mockWebServer.url("/").toString(),
                maxRetries = 0,
            )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500).setBody("Internal error"),
        )

        val result = clientWithNoRetries.sendBatch("""{"event":"snapshot"}""")

        assertThat(result.isFailure).isTrue()
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }
}
