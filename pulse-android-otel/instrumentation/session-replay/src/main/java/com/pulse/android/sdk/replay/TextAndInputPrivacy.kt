package com.pulse.android.sdk.replay

/**
 * Controls which text and input content is masked in session replay.
 *
 * - [MASK_ALL]: mask all text and input fields (default, most restrictive).
 * - [MASK_ALL_INPUTS]: mask only user-editable input fields (EditText, Spinner); show static text.
 * - [MASK_SENSITIVE_INPUTS]: mask only sensitive input types (password, email, phone); show everything else.
 */
public enum class TextAndInputPrivacy {
    MASK_ALL,
    MASK_ALL_INPUTS,
    MASK_SENSITIVE_INPUTS,
}
