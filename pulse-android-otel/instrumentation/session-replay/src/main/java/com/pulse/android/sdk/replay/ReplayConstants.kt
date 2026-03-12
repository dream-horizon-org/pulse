package com.pulse.android.sdk.replay

/**
 * View tag / contentDescription values for per-view masking.
 * - [MASK_TAG]: mask this view (e.g. tag = "pulse-mask" or contentDescription contains it).
 * - [UNMASK_TAG]: do not mask this view, override global config.
 */
public object ReplayConstants {
    public const val MASK_TAG: String = "pulse-mask"
    public const val UNMASK_TAG: String = "pulse-unmask"
}
