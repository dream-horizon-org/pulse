package com.pulse.android.sdk

import android.content.SharedPreferences
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
    }

    @Test
    fun `when userId is not fetched and set with new null and old not null, should emit session end`() {
        sharedPreferences.putString(PulseUserSessionEmitter.USER_PREFS_KEY, "old-user")
        emitter.userId = null
        val logRecords = logExporter.finishedLogRecordItems
        assertThat(logRecords).hasSize(1)
        assertThat(logRecords[0].eventName).isEqualTo(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
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

internal class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? =
        (data[key] as? Set<*>)?.run {
            mapNotNull { it as? String }.toMutableSet()
        } ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = (data[key] as? Number)?.toInt() ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = (data[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = (data[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
    }
}

internal class InMemoryEditor(
    private val data: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    private val changes = mutableMapOf<String, Any?>()
    private val removals = mutableSetOf<String>()

    override fun putString(
        key: String?,
        value: String?,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putStringSet(
        key: String?,
        values: MutableSet<String>?,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = values
        removals.remove(key)
        return this
    }

    override fun putInt(
        key: String?,
        value: Int,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putLong(
        key: String?,
        value: Long,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putFloat(
        key: String?,
        value: Float,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putBoolean(
        key: String?,
        value: Boolean,
    ): SharedPreferences.Editor {
        if (key == null) return this
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
        if (key == null) return this
        removals.add(key)
        changes.remove(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        removals.addAll(data.keys)
        changes.clear()
        return this
    }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        removals.forEach { data.remove(it) }
        changes.forEach { (key, value) ->
            if (value == null) {
                data.remove(key)
            } else {
                data[key] = value
            }
        }
        changes.clear()
        removals.clear()
    }
}
