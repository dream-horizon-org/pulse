# Merged into app R8 when depending on session-replay. Keeps View/Compose masking entry points
# reachable so release minification does not drop tag extensions or Compose semantics keys.

-keep class com.pulse.android.sdk.replay.ui.** { *; }

# MaskingCollector checks for Compose views via Class.forName("...AndroidComposeView") and calls
# getSemanticsOwner() via reflection. -keepclassmembers alone only preserves the method but lets
# R8 rename the class, so Class.forName throws and isComposeAvailable becomes false – silently
# disabling all Compose masking in release builds. -keep preserves both the class name and member.
-keep class androidx.compose.ui.platform.AndroidComposeView {
    public androidx.compose.ui.semantics.SemanticsOwner getSemanticsOwner();
}
