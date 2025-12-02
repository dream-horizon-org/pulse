package com.pulse.android.core

public class InteractionLocalEvent internal constructor(
    public val name: String,
    public val timeInNano: Long,
    public val props: Map<String, String>? = null,
)
