package com.pulse.utils

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Returns a [Flow] that emits the current [String] value of [key] immediately on collection,
 * then emits again whenever the value changes. Emission stops when the collecting coroutine
 * is canceled.
 */
public inline fun <reified T> SharedPreferences.toFlow(
    key: String,
): Flow<T?> = callbackFlow {
    trySend(readValue(key, null as T?))

    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            trySend(readValue(key, null as T?))
        }
    }

    registerOnSharedPreferenceChangeListener(listener)

    awaitClose {
        unregisterOnSharedPreferenceChangeListener(listener)
    }
}

@PublishedApi
internal inline fun <reified T> SharedPreferences.readValue(
    key: String,
    defaultValue: T? = null,
): T? {
    @Suppress("IMPLICIT_CAST_TO_ANY")
    val result = when (T::class) {
        String::class -> getString(key, defaultValue as? String)
        Int::class -> {
            if (contains(key)) getInt(key, defaultValue as? Int ?: 0) else defaultValue
        }
        Long::class -> {
            if (contains(key)) getLong(key, defaultValue as? Long ?: 0L) else defaultValue
        }
        Float::class -> {
            if (contains(key)) getFloat(key, defaultValue as? Float ?: 0f) else defaultValue
        }
        Boolean::class -> {
            if (contains(key)) getBoolean(key, defaultValue as? Boolean ?: false) else defaultValue
        }
        Set::class -> {
            @Suppress("UNCHECKED_CAST")
            getStringSet(key, defaultValue as? Set<String>)
        }
        else -> error("Unsupported SharedPreferences type: ${T::class}")
    }

    return result as T?
}
