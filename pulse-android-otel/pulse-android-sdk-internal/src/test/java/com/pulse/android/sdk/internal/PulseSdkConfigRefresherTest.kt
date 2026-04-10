@file:Suppress("InjectDispatcher")

package com.pulse.android.sdk.internal

import com.pulse.sampling.models.PulseSdkConfig
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.utils.PulseSerialisationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PulseSdkConfigRefresherTest {
    @TempDir
    lateinit var tempDir: File

    @Nested
    inner class ResolveConfigUrl {
        @Test
        fun `when explicit configEndpointUrl is provided, it is returned as-is`() {
            val explicit = "https://custom.example.com/my-config/"

            val result =
                PulseSdkConfigRefresher.resolveConfigUrl(
                    configEndpointUrl = explicit,
                    endpointBaseUrl = "https://ignored.example.com:4318",
                )

            assertThat(result).isEqualTo(explicit)
        }

        @Test
        fun `when configEndpointUrl is null, derives url from endpointBaseUrl`() {
            val result =
                PulseSdkConfigRefresher.resolveConfigUrl(
                    configEndpointUrl = null,
                    endpointBaseUrl = "https://collector.example.com:4318",
                )

            assertThat(result).isEqualTo("https://collector.example.com:8080/v1/configs/active/")
        }

        @Test
        fun `when configEndpointUrl is null and baseUrl has no port, appends config path`() {
            val result =
                PulseSdkConfigRefresher.resolveConfigUrl(
                    configEndpointUrl = null,
                    endpointBaseUrl = "https://collector.example.com",
                )

            assertThat(result).isEqualTo("https://collector.example.com/v1/configs/active/")
        }

        @Test
        fun `when configEndpointUrl is null and baseUrl already has trailing slash, does not double slash`() {
            val result =
                PulseSdkConfigRefresher.resolveConfigUrl(
                    configEndpointUrl = null,
                    endpointBaseUrl = "https://collector.example.com:4318/",
                )

            assertThat(result).doesNotContain("//v1")
        }
    }

    @Nested
    inner class LoadAndRefresh {
        private lateinit var server: MockWebServer
        private lateinit var sharedPrefs: InMemorySharedPreferences
        private val prefsKey = "sdk_config"

        @BeforeEach
        fun setUp() {
            server = MockWebServer()
            server.start()
            sharedPrefs = InMemorySharedPreferences()
        }

        @AfterEach
        fun tearDown() {
            server.shutdown()
        }

        private fun configUrl() = server.url("/v1/configs/active/").toString()

        private fun enqueueConfig(version: Int) {
            val config = PulseSdkConfigFakeUtils.createFakeConfig(version = version)
            val json = PulseSerialisationUtils.jsonConfigForSerialisation.encodeToString(config)
            server.enqueue(MockResponse().setBody(json).setResponseCode(200))
        }

        private fun storeConfig(version: Int) {
            val config = PulseSdkConfigFakeUtils.createFakeConfig(version = version)
            val json = PulseSerialisationUtils.jsonConfigForSerialisation.encodeToString(config)
            sharedPrefs.putString(prefsKey, json)
        }

        private fun storedConfig(): PulseSdkConfig? =
            sharedPrefs.getString(prefsKey, null)?.let {
                PulseSerialisationUtils.jsonConfigForSerialisation.decodeFromString(it)
            }

        @Test
        fun `when SharedPreferences has a cached config, it is returned immediately`() =
            runTest {
                storeConfig(version = 2)
                enqueueConfig(version = 3)

                val result =
                    PulseSdkConfigRefresher.loadAndRefresh(
                        cacheDir = tempDir,
                        headers = emptyMap(),
                        configUrl = configUrl(),
                        sharedPrefs = sharedPrefs,
                        prefsKey = prefsKey,
                        scope = this,
                        ioDispatcher = StandardTestDispatcher(testScheduler),
                    )

                assertThat(result?.version).isEqualTo(2)
            }

        @Test
        fun `when SharedPreferences has no cached config, null is returned`() =
            runTest {
                enqueueConfig(version = 1)

                val result =
                    PulseSdkConfigRefresher.loadAndRefresh(
                        cacheDir = tempDir,
                        headers = emptyMap(),
                        configUrl = configUrl(),
                        sharedPrefs = sharedPrefs,
                        prefsKey = prefsKey,
                        scope = this,
                        ioDispatcher = StandardTestDispatcher(testScheduler),
                    )

                assertThat(result).isNull()
            }

        @Test
        fun `when server returns a config with a new version, it is persisted after background refresh`() {
            runBlocking {
                storeConfig(version = 1)
                enqueueConfig(version = 2)

                PulseSdkConfigRefresher.loadAndRefresh(
                    cacheDir = tempDir,
                    headers = emptyMap(),
                    configUrl = configUrl(),
                    sharedPrefs = sharedPrefs,
                    prefsKey = prefsKey,
                    scope = this,
                    ioDispatcher = Dispatchers.IO,
                )
            }
            assertThat(storedConfig()?.version).isEqualTo(2)
        }

        @Test
        fun `when server returns a config with the same version, SharedPreferences is not updated`() {
            runBlocking {
                storeConfig(version = 1)
                enqueueConfig(version = 1)
                val originalJson = sharedPrefs.getString(prefsKey, null)

                PulseSdkConfigRefresher.loadAndRefresh(
                    cacheDir = tempDir,
                    headers = emptyMap(),
                    configUrl = configUrl(),
                    sharedPrefs = sharedPrefs,
                    prefsKey = prefsKey,
                    scope = this,
                    ioDispatcher = Dispatchers.IO,
                )

                assertThat(sharedPrefs.getString(prefsKey, null)).isEqualTo(originalJson)
            }
        }

        @Test
        fun `when server returns an error, SharedPreferences is not updated`() {
            runBlocking {
                storeConfig(version = 1)
                server.enqueue(MockResponse().setResponseCode(500))
                val originalJson = sharedPrefs.getString(prefsKey, null)

                PulseSdkConfigRefresher.loadAndRefresh(
                    cacheDir = tempDir,
                    headers = emptyMap(),
                    configUrl = configUrl(),
                    sharedPrefs = sharedPrefs,
                    prefsKey = prefsKey,
                    scope = this,
                    ioDispatcher = Dispatchers.IO,
                )

                assertThat(sharedPrefs.getString(prefsKey, null)).isEqualTo(originalJson)
            }
        }

        @Test
        fun `when background refresh runs, the request carries the provided headers`() {
            runBlocking {
                storeConfig(version = 1)
                enqueueConfig(version = 2)

                PulseSdkConfigRefresher.loadAndRefresh(
                    cacheDir = tempDir,
                    headers = mapOf("X-API-KEY" to "project-abc"),
                    configUrl = configUrl(),
                    sharedPrefs = sharedPrefs,
                    prefsKey = prefsKey,
                    scope = this,
                    ioDispatcher = Dispatchers.IO,
                )

                val recordedRequest = server.takeRequest()
                assertThat(recordedRequest.getHeader("X-API-KEY")).isEqualTo("project-abc")
            }
        }
    }
}
