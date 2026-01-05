package com.pulse.android.sdk

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.pulse.semconv.PulseAttributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.AppIncubatingAttributes
import java.util.UUID

/**
 * Manages the installation ID for the app instance.
 * The installation ID remains constant for the entire app installation
 * and only gets reset when the user uninstalls the app.
 */
internal class PulseInstallationIdManager(
    private val sharedPrefs: SharedPreferences,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val loggerProvider: () -> Logger,
) {
    /**
     * Gets the installation ID, generating a new one if it doesn't exist.
     * This ID persists for the entire app installation and is only reset on uninstall.
     * Uses lazy initialization with thread-safe synchronization to ensure only one ID is generated
     * even under concurrent access.
     */
    val installationId: String by lazy {
        sharedPrefs.getString(INSTALLATION_ID_PREFS_KEY, null) ?: generateAndStoreInstallationId()
    }

    private fun generateAndStoreInstallationId(): String {
        val newId = UUID.randomUUID().toString()
        sharedPrefs.edit { putString(INSTALLATION_ID_PREFS_KEY, newId) }

        // posting event at the end of event queue so that when logger is access otel is already initialized
        handler.post {
            val logger = loggerProvider()
            logger
                .logRecordBuilder()
                .setAttribute(AppIncubatingAttributes.APP_INSTALLATION_ID, newId)
                .setEventName(PulseAttributes.PulseTypeValues.APP_INSTALLATION_START)
                .setAttribute(PulseAttributes.PULSE_TYPE, PulseAttributes.PulseTypeValues.APP_INSTALLATION_START)
                .emit()
        }

        return newId
    }

    companion object {
        internal const val INSTALLATION_ID_PREFS_KEY = "installation_id"
    }
}
