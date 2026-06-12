package com.pulse.android.sdk.internal

import com.pulse.semconv.PulseAttributes
import com.pulse.semconv.PulseUserAttributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PulseUserSessionEmitterTest {
    private lateinit var logger: Logger
    private val logExporter: InMemoryLogRecordExporter = InMemoryLogRecordExporter.create()
    private lateinit var sharedPreferences: InMemorySharedPreferences
    private lateinit var emitter: PulseUserSessionEmitter

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
        emitter = PulseUserSessionEmitter({ logger }, sharedPreferences)
    }

    @Test
    fun `when userId is not fetched and get is called multiple times, should read from SharedPreferences`() {
        sharedPreferences.putString(PulseUserSessionEmitter.USER_PREFS_KEY, "existing-user")
        assertThat(emitter.userId).isEqualTo("existing-user")
        assertThat(emitter.userId).isEqualTo("existing-user")
    }

    @Test
    fun `when userId is not fetched and get is called multiple times with no value in prefs, should return null`() {
        val result = emitter.userId
        assertThat(result).isNull()
        assertThat(result).isNull()
    }

    @Test
    fun `when userId is fetched and get is called, should return cached value`() {
        emitter.userId = "cached-user"
        val result = emitter.userId
        assertThat(result).isEqualTo("cached-user")
        assertThat(result).isEqualTo("cached-user")
        assertThat(sharedPreferences.getString(PulseUserSessionEmitter.USER_PREFS_KEY, null)).isEqualTo("cached-user")
    }

    @Test
    fun `when userId is not fetched and set with new null and old not null, should emit session end`() {
        sharedPreferences.putString(PulseUserSessionEmitter.USER_PREFS_KEY, "old-user")
        emitter.userId = null
        assertThat(sharedPreferences.getString(PulseUserSessionEmitter.USER_PREFS_KEY, "not null")).isEqualTo(null)
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(1)
        assertThat(logRecords[0].eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
        assertThat(
            logRecords[0].attributes.get(PulseAttributes.PULSE_TYPE),
        ).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
        assertThat(logRecords[0].attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("old-user")
        assertThat(logRecords[0].attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()

        logExporter.reset()
        emitter.userId = null
        assertThat(logExporter.finishedLogRecordItems).isEmpty()
    }

    @Test
    fun `when userId is not fetched and set with new not null and old null, should emit session start`() {
        emitter.userId = "new-user"
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(1)
        assertThat(logRecords[0].eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
        assertThat(
            logRecords[0].attributes.get(PulseAttributes.PULSE_TYPE),
        ).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
        assertThat(logRecords[0].attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("new-user")
        assertThat(logRecords[0].attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()

        logExporter.reset()
        emitter.userId = "new-user"
        assertThat(logExporter.finishedLogRecordItems).isEmpty()
    }

    @Test
    fun `when userId is not fetched and set with both not null and different, should emit session end and start`() {
        sharedPreferences.putString(PulseUserSessionEmitter.USER_PREFS_KEY, "old-user")
        emitter.userId = "new-user"
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(2)
        assertThat(logRecords[0].eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
        assertThat(logRecords[0].attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("old-user")
        assertThat(logRecords[0].attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()
        assertThat(logRecords[1].eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
        assertThat(logRecords[1].attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("new-user")
        assertThat(logRecords[1].attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isEqualTo("old-user")

        logExporter.reset()
        emitter.userId = "new-user"
        assertThat(logExporter.finishedLogRecordItems).isEmpty()
    }

    @Test
    fun `when userId is not fetched and set with both null, should not emit any events`() {
        emitter.userId = null
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).isEmpty()
    }

    @Test
    fun `when userId is fetched and set with new null and old not null, should emit session end`() {
        emitter.userId = "existing-user"
        val initialLogCount = logExporter.finishedLogRecordItems.size
        emitter.userId = null
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(initialLogCount + 1)
        val lastLog = logRecords[logRecords.size - 1]
        assertThat(lastLog.eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
        assertThat(lastLog.attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("existing-user")
        assertThat(lastLog.attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()
    }

    @Test
    fun `when userId is fetched and set with new not null and old null, should emit session start`() {
        emitter.userId = null
        val initialLogCount = logExporter.finishedLogRecordItems.size
        emitter.userId = "new-user"
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(initialLogCount + 1)
        val lastLog = logRecords[logRecords.size - 1]
        assertThat(lastLog.eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
        assertThat(lastLog.attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("new-user")
        assertThat(lastLog.attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()
    }

    @Test
    fun `when userId is fetched and set with both not null and different, should emit session end and start`() {
        emitter.userId = "old-user"
        val initialLogCount = logExporter.finishedLogRecordItems.size
        emitter.userId = "new-user"
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(initialLogCount + 2)
        val endLog = logRecords[logRecords.size - 2]
        val startLog = logRecords[logRecords.size - 1]
        assertThat(endLog.eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
        assertThat(endLog.attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("old-user")
        assertThat(endLog.attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isNull()
        assertThat(startLog.eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
        assertThat(startLog.attributes.get(UserIncubatingAttributes.USER_ID)).isEqualTo("new-user")
        assertThat(startLog.attributes.get(PulseUserAttributes.PULSE_USER_PREVIOUS_ID)).isEqualTo("old-user")
    }

    @Test
    fun `when userId is fetched and set with both null, should not emit any events`() {
        emitter.userId = null
        val initialLogCount = logExporter.finishedLogRecordItems.size
        emitter.userId = null
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(initialLogCount)
    }

    @Test
    fun `when userId is fetched and set with same value, should not emit any events`() {
        emitter.userId = "same-user"
        val initialLogCount = logExporter.finishedLogRecordItems.size
        emitter.userId = "same-user"
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(initialLogCount)
    }
}
