package com.pulse.android.sdk

import android.os.Handler
import com.pulse.semconv.PulseAttributes
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PulseInstallationIdManagerTest {
    private lateinit var logger: Logger
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private lateinit var sharedPreferences: InMemorySharedPreferences
    private lateinit var installationIdManager: PulseInstallationIdManager

    @BeforeEach
    fun setUp() {
        logExporter.reset()
        val loggerProvider =
            SdkLoggerProvider
                .builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .build()
        logger = loggerProvider.loggerBuilder("test").build()
        sharedPreferences = InMemorySharedPreferences()
        val mockedHandler = mockk<Handler>(relaxed = true)
        every { mockedHandler.post(any<Runnable>()) } answers {
            firstArg<Runnable>().run()
            true
        }
        installationIdManager = PulseInstallationIdManager(sharedPreferences, mockedHandler) { logger }
    }

    @Test
    fun `when installation ID does not exist, should generate and store a new UUID`() {
        val installationId = installationIdManager.installationId

        assertThat(installationId).isNotNull()
        assertThat(sharedPreferences.getString(PulseInstallationIdManager.INSTALLATION_ID_PREFS_KEY, null))
            .isEqualTo(installationId)
    }

    @Test
    fun `when installation ID is first generated, should emit app installation start event`() {
        val installationId = installationIdManager.installationId

        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(1)
        assertThat(logRecords[0].eventName).isEqualTo(PulseAttributes.PulseTypeValues.APP_INSTALLATION_START)
        assertThat(logRecords[0].attributes.get(PulseAttributes.PULSE_TYPE))
            .isEqualTo(PulseAttributes.PulseTypeValues.APP_INSTALLATION_START)
        assertThat(logRecords[0].attributes.get(AppIncubatingAttributes.APP_INSTALLATION_ID))
            .isEqualTo(installationId)
    }

    @Test
    fun `when installation ID already exists, should not emit app installation start event`() {
        val existingId = "550e8400-e29b-41d4-a716-446655440000"
        sharedPreferences.putString(PulseInstallationIdManager.INSTALLATION_ID_PREFS_KEY, existingId)

        val installationId = installationIdManager.installationId

        assertThat(installationId).isEqualTo(existingId)
        assertThat(logExporter.finishedLogRecordItems).isEmpty()
    }

    @Test
    fun `when installation ID exists in SharedPreferences, should return the existing ID`() {
        val existingId = "550e8400-e29b-41d4-a716-446655440000"
        sharedPreferences.putString(PulseInstallationIdManager.INSTALLATION_ID_PREFS_KEY, existingId)

        val installationId = installationIdManager.installationId

        assertThat(installationId).isEqualTo(existingId)
    }

    @Test
    fun `when installation ID is accessed multiple times, should return the same ID`() {
        val firstAccess = installationIdManager.installationId
        val secondAccess = installationIdManager.installationId
        val thirdAccess = installationIdManager.installationId

        assertThat(firstAccess).isEqualTo(secondAccess)
        assertThat(secondAccess).isEqualTo(thirdAccess)
        assertThat(firstAccess).isEqualTo(thirdAccess)
    }

    @Test
    fun `when installation ID is accessed multiple times, should only store one ID in SharedPreferences`() {
        val firstAccess = installationIdManager.installationId
        val secondAccess = installationIdManager.installationId

        val storedId = sharedPreferences.getString(PulseInstallationIdManager.INSTALLATION_ID_PREFS_KEY, null)

        assertThat(storedId).isEqualTo(firstAccess)
        assertThat(storedId).isEqualTo(secondAccess)
    }

    @Test
    fun `when multiple instances access the same SharedPreferences, should return the same ID`() {
        val firstManager = PulseInstallationIdManager(sharedPreferences) { logger }
        val firstId = firstManager.installationId

        val secondManager = PulseInstallationIdManager(sharedPreferences) { logger }
        val secondId = secondManager.installationId

        assertThat(firstId).isEqualTo(secondId)
    }

    @Test
    fun `when SharedPreferences is cleared and new manager is created, should generate a new ID`() {
        val firstId = installationIdManager.installationId

        sharedPreferences.edit().clear().apply()

        val newManager = PulseInstallationIdManager(sharedPreferences) { logger }
        val newId = newManager.installationId

        assertThat(newId).isNotEqualTo(firstId)
    }

    @Test
    fun `when installation ID is accessed concurrently from multiple threads, should return the same value`() {
        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<String>()

        try {
            repeat(threadCount) {
                executor.submit {
                    val id = installationIdManager.installationId
                    synchronized(results) {
                        results.add(id)
                    }
                    latch.countDown()
                }
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

            assertThat(results).hasSize(threadCount)
            val uniqueIds = results.toSet()
            assertThat(uniqueIds).hasSize(1)

            val installationId = uniqueIds.first()
            assertThat(sharedPreferences.getString(PulseInstallationIdManager.INSTALLATION_ID_PREFS_KEY, null))
                .isEqualTo(installationId)

            val logRecords = logExporter.finishedLogRecordItems
            assertThat(logRecords).hasSize(1)
        } finally {
            executor.shutdown()
        }
    }
}
