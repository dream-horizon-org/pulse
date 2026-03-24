package com.pulse.android.sdk.replay

/**
 * Controls which images are masked in session replay.
 *
 * - [MASK_ALL]: replace all images with a mask (default, most restrictive).
 * - [MASK_NONE]: show all images without masking.
 */
public enum class ImagePrivacy {
    MASK_ALL,
    MASK_NONE,
}
