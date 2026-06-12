package com.pulse.android.remote.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
public class InteractionEvent internal constructor(
    public val name: String,
    public val props: List<InteractionAttrsEntry>?,
    public val isBlacklisted: Boolean,
)
