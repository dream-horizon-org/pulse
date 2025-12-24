package com.pulse.android.sdk

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pulse.semconv.PulseUserAttributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes

internal class PulseUserSessionEmitter(
    private val loggerProvider: () -> Logger,
    private val userPref: SharedPreferences,
) {
    private var _userId: String? = null
    private var isUserIdFetched: Boolean = false
    var userId: String?
        get() =
            if (isUserIdFetched) {
                _userId
            } else {
                _userId = userPref.getString(USER_PREFS_KEY, null)
                isUserIdFetched = true
                _userId
            }
        set(value) {
            if (isUserIdFetched) {
                if (_userId == value) {
                    return
                } else {
                    updateUserId(value, _userId)
                }
            } else {
                updateUserId(value, userPref.getString(USER_PREFS_KEY, null))
            }
            isUserIdFetched = true
            _userId = value
        }

    private fun updateUserId(
        newUserId: String?,
        oldUserId: String?,
    ) {
        if (newUserId == oldUserId) return
        userPref.edit { putString(USER_PREFS_KEY, newUserId) }
        val logger = loggerProvider()
        if (oldUserId != null) {
            logger
                .logRecordBuilder()
                .setAttribute(UserIncubatingAttributes.USER_ID, oldUserId)
                .setEventName(PulseUserAttributes.PULSE_USER_SESSION_END_EVENT_NAME)
                .emit()
        }

        if (newUserId != null) {
            logger
                .logRecordBuilder()
                .setAttribute(UserIncubatingAttributes.USER_ID, newUserId)
                .setAttribute(PulseUserAttributes.PULSE_USER_PREVIOUS_ID, oldUserId)
                .setEventName(PulseUserAttributes.PULSE_USER_SESSION_START_EVENT_NAME)
                .emit()
        }
    }

    companion object {
        internal const val USER_PREFS_KEY = "user_id"
    }
}
