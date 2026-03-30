package com.pulse.android.sdk.replay

/**
 * View tag / contentDescription values for per-view masking.
 * - [MASK_TAG]: mask this view (e.g. tag = "pulse-mask" or contentDescription contains it).
 * - [UNMASK_TAG]: do not mask this view, override global config.
 *
 * [REPLAY_LOG_TAG]: tag for PulseOtelUtils logging (logcat filter: PulseSessionReplay).
 */
public object ReplayConstants {
    public const val MASK_TAG: String = "pulse-mask"
    public const val UNMASK_TAG: String = "pulse-unmask"
    public const val REPLAY_LOG_TAG: String = "PulseSessionReplay"
}
