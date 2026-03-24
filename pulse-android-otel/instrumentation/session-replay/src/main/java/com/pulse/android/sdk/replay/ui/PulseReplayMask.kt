package com.pulse.android.sdk.replay.ui

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.pulse.android.sdk.replay.ReplayConstants

/**
 * Semantics key for Compose masking. Internal for use by [com.pulse.android.sdk.replay.internal.capture.MaskingCollector].
 */
internal val PulseReplayMaskKey = SemanticsPropertyKey<Boolean>(ReplayConstants.MASK_TAG)

/**
 * Modifier to mask or unmask this element in session replay.
 * Equivalent to [View.setPulseReplayMask] / [View.setPulseReplayUnmask] for Views.
 *
 * @param isEnabled If true, the element will be masked; if false, unmasked (overrides global defaults).
 */
public fun Modifier.setPulseReplayMask(isEnabled: Boolean = true): Modifier = semantics(properties = { set(PulseReplayMaskKey, isEnabled) })
