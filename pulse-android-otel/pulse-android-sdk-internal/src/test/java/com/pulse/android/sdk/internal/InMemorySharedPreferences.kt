package com.pulse.android.sdk.internal

import android.content.SharedPreferences

internal class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = if (data.containsKey(key)) data[key] as? String? else defValue

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? =
        (data[key] as? Set<*>)?.run {
            mapNotNull { it as? String }.toMutableSet()
        } ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = (data[key] as? Number)?.toInt() ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = (data[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = (data[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    fun putString(
        key: String,
        value: String?,
    ) {
        data[key] = value
    }
}

internal class InMemoryEditor(
    private val data: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    private val changes = mutableMapOf<String, Any?>()
    private val removals = mutableSetOf<String>()

    override fun putString(
        key: String,
        value: String?,
    ): SharedPreferences.Editor {
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putStringSet(
        key: String,
        values: MutableSet<String>?,
    ): SharedPreferences.Editor {
        changes[key] = values
        removals.remove(key)
        return this
    }

    override fun putInt(
        key: String,
        value: Int,
    ): SharedPreferences.Editor {
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putLong(
        key: String,
        value: Long,
    ): SharedPreferences.Editor {
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putFloat(
        key: String,
        value: Float,
    ): SharedPreferences.Editor {
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ): SharedPreferences.Editor {
        changes[key] = value
        removals.remove(key)
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
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
            data[key] = value
        }
        changes.clear()
        removals.clear()
    }
}
