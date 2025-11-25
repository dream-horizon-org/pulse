package com.pulse.android.core

public data class InteractionLocalEvent(
    val name: String,
    val timeInNano: Long,
    val props: Map<String, String>? = null
)
