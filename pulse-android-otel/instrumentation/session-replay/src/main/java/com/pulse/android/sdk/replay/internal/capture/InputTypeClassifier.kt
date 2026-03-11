package com.pulse.android.sdk.replay.internal.capture

import android.text.InputType

/**
 * Shared input type classification used by both [MaskingCollector] (screenshot mode)
 * and [WireframeCapture] (wireframe mode).
 */
internal object InputTypeClassifier {

    fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    fun isSensitiveInputType(inputType: Int): Boolean {
        if (isPasswordInputType(inputType)) return true
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            cls == InputType.TYPE_CLASS_PHONE
    }
}
