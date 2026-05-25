package io.opentelemetry.android.internal.services.visiblescreen

data class VisibleScreenState(
    val screenName: String,
    val activityName: String?,
    val fragmentName: String?,
    val previouslyVisibleScreen: String?,
    val previouslyVisibleActivity: String?,
    val previouslyVisibleFragment: String?,
)
