package com.pulse.android.remote.models

import androidx.annotation.Keep

@Keep
public data class InteractionEvent(
    val name: String,
    val props: List<InteractionAttrsEntry>?,
    val isBlacklisted: Boolean
)