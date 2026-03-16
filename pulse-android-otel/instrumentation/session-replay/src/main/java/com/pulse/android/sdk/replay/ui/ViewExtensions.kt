package com.pulse.android.sdk.replay.ui

import android.view.View
import com.pulse.android.sdk.replay.R

private const val PRIVACY_MASK = "mask"
private const val PRIVACY_UNMASK = "unmask"

/**
 * Mark this view to be masked in session replay.
 * Overrides global config and class-level settings for this specific view instance.
 */
public fun View.pulseReplayMask() {
    setTag(R.id.pulse_replay_privacy, PRIVACY_MASK)
}

/**
 * Mark this view to be unmasked (shown) in session replay.
 * Overrides global config and class-level settings for this specific view instance.
 * Also overrides parent ViewGroup masking propagation.
 */
public fun View.pulseReplayUnmask() {
    setTag(R.id.pulse_replay_privacy, PRIVACY_UNMASK)
}

internal fun View.getPrivacyTagValue(): String? =
    getTag(R.id.pulse_replay_privacy) as? String

internal fun View.hasPrivacyMaskTag(): Boolean = getPrivacyTagValue() == PRIVACY_MASK

internal fun View.hasPrivacyUnmaskTag(): Boolean = getPrivacyTagValue() == PRIVACY_UNMASK
