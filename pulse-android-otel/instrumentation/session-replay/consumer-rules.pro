# Merged into app R8 when depending on session-replay. Keeps View/Compose masking entry points
# reachable so release minification does not drop tag extensions or Compose semantics keys.

-keep class com.pulse.android.sdk.replay.ui.** { *; }

# MaskingCollector obtains SemanticsOwner via reflection (getSemanticsOwner); keep the accessor on Compose roots.
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    public androidx.compose.ui.semantics.SemanticsOwner getSemanticsOwner();
}
