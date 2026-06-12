package com.pulse.android.remote.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class InteractionAttrsEntry internal constructor(
    public val name: String,
    public val value: String,
    public val operator: String,
)
