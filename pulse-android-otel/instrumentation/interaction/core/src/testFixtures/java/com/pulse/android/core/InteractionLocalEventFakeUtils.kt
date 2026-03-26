@file:Suppress("RedundantVisibilityModifier", "unused") // explicit api requires public modifier mentioned

package com.pulse.android.core

public object InteractionLocalEventFakeUtils {
    public fun createFakeInteractionLocalEvent(
        name: String = "fake-event",
        timeInNano: Long = 0L,
        props: Map<String, String>? = null,
    ): InteractionLocalEvent = InteractionLocalEvent(name, timeInNano, props)
}
