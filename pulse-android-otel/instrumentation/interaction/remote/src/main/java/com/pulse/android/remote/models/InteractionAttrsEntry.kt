package com.pulse.android.remote.models

import androidx.annotation.Keep

@Keep
public data class InteractionAttrsEntry(
    val name: String,
    val value: String,
    val operator: String,
)