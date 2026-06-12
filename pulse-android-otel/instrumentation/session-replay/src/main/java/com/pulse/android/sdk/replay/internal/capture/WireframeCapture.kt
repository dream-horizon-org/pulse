package com.pulse.android.sdk.replay.internal.capture

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.view.isNotEmpty
import com.pulse.android.sdk.replay.ImagePrivacy
import com.pulse.android.sdk.replay.ReplayConstants
import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.TextAndInputPrivacy
import com.pulse.android.sdk.replay.events.InputWireframeType
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.events.WireframeType
import com.pulse.android.sdk.replay.internal.util.isValid
import com.pulse.android.sdk.replay.internal.util.webpBase64
import com.pulse.android.sdk.replay.ui.hasPrivacyMaskTag
import com.pulse.android.sdk.replay.ui.hasPrivacyUnmaskTag
import java.util.Locale

/**
 * View-to-wireframe capture. View-based only; no Compose wireframes.
 * Masking logic is aligned with [MaskingCollector] (same priority system).
 */
internal object WireframeCapture {
    private const val ALIGN_CENTER = "center"
    private const val ALIGN_RIGHT = "right"
    private const val ALIGN_LEFT = "left"

    fun toWireframe(
        view: View,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
        logger: (String) -> Unit,
    ): ReplayWireframe? = view.toWireframeInternal(null, config, displayMetrics, logger)

    fun themeToRGBColor(theme: android.content.res.Resources.Theme): String? {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, value, true)
        return if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            value.data.toRGBColor()
        } else {
            null
        }
    }

    private class WireframeProps(
        style: ReplayStyle,
    ) {
        var base64: String? = null
        var type: String? = null
        var style: ReplayStyle = style
        var isChecked: Boolean? = null
        var text: String? = null
        var inputType: String? = null
        var value: Any? = null
        var label: String? = null
        var options: List<String>? = null
        var max: Int? = null
    }

    private fun View.toWireframeInternal(
        parentId: Int?,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
        logger: (String) -> Unit,
    ): ReplayWireframe? {
        if (!ScreenshotCapture.isVisible(this, logger)) return null
        val viewId = System.identityHashCode(this)
        val coordinates = IntArray(2)
        if (ScreenshotCapture.isViewStateStable(this, logger)) {
            getLocationOnScreen(coordinates)
        }
        val dpX = (coordinates[0] / displayMetrics.density).toInt()
        val dpY = (coordinates[1] / displayMetrics.density).toInt()
        val dpWidth = (this.width / displayMetrics.density).toInt()
        val dpHeight = (this.height / displayMetrics.density).toInt()

        val bgColor = background?.toRGBColor()
        val bgImage = if (bgColor == null) background?.base64(this.width, this.height, config, displayMetrics) else null
        val props = WireframeProps(ReplayStyle(backgroundColor = bgColor, backgroundImage = bgImage))

        if (id == android.R.id.statusBarBackground) props.type = WireframeType.STATUS_BAR
        if (id == android.R.id.navigationBarBackground) props.type = WireframeType.NAVIGATION_BAR

        if (this is TextView) captureTextView(props, config, displayMetrics)
        captureWidgetType(props, config, displayMetrics)

        val children = mutableListOf<ReplayWireframe>()
        if (this is ViewGroup && isNotEmpty()) {
            for (i in 0 until childCount) {
                getChildAt(i)?.toWireframeInternal(viewId, config, displayMetrics, logger)?.let { children.add(it) }
            }
        }
        return ReplayWireframe(
            id = viewId,
            x = dpX,
            y = dpY,
            width = dpWidth,
            height = dpHeight,
            text = props.text,
            type = props.type,
            style = props.style,
            childWireframes = children.ifEmpty { null },
            base64 = props.base64,
            parentId = parentId,
            isDisabled = !isEnabled,
            isChecked = props.isChecked,
            inputType = props.inputType,
            value = props.value,
            label = props.label,
            options = props.options,
            max = props.max,
        )
    }

    private fun TextView.captureTextView(
        props: WireframeProps,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
    ) {
        val viewText = this.text?.toString()
        if (!viewText.isNullOrBlank()) {
            props.text = if (!shouldMaskTextView(config)) viewText else viewText.mask()
        }
        val hint = this.hint?.toString()
        if (props.text.isNullOrBlank() && !hint.isNullOrBlank()) {
            props.text = if (!shouldMaskTextView(config)) hint else hint.mask()
        }
        props.type = WireframeType.TEXT

        val fontFamilyStr =
            when (typeface) {
                Typeface.DEFAULT -> "sans-serif"
                Typeface.DEFAULT_BOLD -> "sans-serif-bold"
                Typeface.MONOSPACE -> "monospace"
                Typeface.SERIF -> "serif"
                else -> null
            }
        val fontSizeVal = (textSize / displayMetrics.density).toInt()
        val alignment = resolveAlignment()
        val iconLeft = compoundDrawables.getOrNull(0)?.base64(this.width, this.height, config, displayMetrics)
        val iconRight = compoundDrawables.getOrNull(2)?.base64(this.width, this.height, config, displayMetrics)

        props.style =
            props.style.copy(
                color = currentTextColor.toRGBColor(),
                fontFamily = fontFamilyStr,
                fontSize = fontSizeVal,
                horizontalAlign = alignment.first,
                verticalAlign = alignment.second,
                paddingTop = if (alignment.second != ALIGN_CENTER) (totalPaddingTop / displayMetrics.density).toInt() else null,
                paddingBottom = if (alignment.second != ALIGN_CENTER) (totalPaddingBottom / displayMetrics.density).toInt() else null,
                paddingLeft = if (alignment.first != ALIGN_CENTER) (totalPaddingLeft / displayMetrics.density).toInt() else null,
                paddingRight = if (alignment.first != ALIGN_CENTER) (totalPaddingRight / displayMetrics.density).toInt() else null,
                iconLeft = iconLeft,
                iconRight = iconRight,
            )

        if (this is Button && this !is CompoundButton) {
            props.style = props.style.copy(borderWidth = 1, borderColor = "#000000")
            props.type = WireframeType.INPUT
            props.inputType = InputWireframeType.BUTTON
            props.value = props.text
            props.text = null
        }
    }

    private fun View.captureWidgetType(
        props: WireframeProps,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
    ) {
        if (this is CheckBox) captureCheckBox(props)
        if (this is RadioGroup) props.type = WireframeType.RADIO_GROUP
        if (this is RadioButton) captureRadioButton(props)
        if (this is EditText) captureEditText(props)
        if (this is Spinner) captureSpinner(props, config)
        if (this is ImageView) captureImageView(props, config, displayMetrics)
        if (this is ProgressBar) captureProgressBar(props)
        if (this is RatingBar) captureRatingBar(props)
        if (this is Switch) captureSwitch(props)
        if (this is WebView) props.type = WireframeType.WEB_VIEW
    }

    private fun CheckBox.captureCheckBox(props: WireframeProps) {
        props.type = WireframeType.INPUT
        props.inputType = InputWireframeType.CHECKBOX
        props.label = props.text
        props.text = null
        props.isChecked = isChecked
    }

    private fun RadioButton.captureRadioButton(props: WireframeProps) {
        props.type = WireframeType.INPUT
        props.inputType = InputWireframeType.RADIO
        props.label = props.text
        props.text = null
        props.isChecked = isChecked
    }

    private fun EditText.captureEditText(props: WireframeProps) {
        props.type = WireframeType.INPUT
        props.inputType = InputWireframeType.TEXT_AREA
        props.value = props.text
        props.text = null
    }

    private fun Spinner.captureSpinner(
        props: WireframeProps,
        config: SessionReplayConfig,
    ) {
        props.type = WireframeType.INPUT
        props.inputType = InputWireframeType.SELECT
        val shouldMask = shouldMaskSpinner(config)
        selectedItem?.let {
            props.value = if (!shouldMask) it.toString() else it.toString().mask()
        }
        adapter?.let { adapter ->
            val items = mutableListOf<String>()
            for (i in 0 until adapter.count) {
                adapter.getItem(i)?.run {
                    val item = toString()
                    items.add(if (!shouldMask) item else item.mask())
                }
            }
            props.options = items.ifEmpty { null }
        }
    }

    private fun ImageView.captureImageView(
        props: WireframeProps,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
    ) {
        props.type = WireframeType.IMAGE
        if (!shouldMaskImage(config, displayMetrics)) {
            drawable?.let { props.base64 = it.base64(width, height, config, displayMetrics) }
        }
    }

    private fun ProgressBar.captureProgressBar(props: WireframeProps) {
        props.inputType = InputWireframeType.PROGRESS
        props.type = WireframeType.INPUT
        if (isIndeterminate) {
            props.style = props.style.copy(bar = "circular")
        } else {
            props.style = props.style.copy(bar = "horizontal")
            props.max = this.max
            props.value = progress
        }
    }

    private fun RatingBar.captureRatingBar(props: WireframeProps) {
        props.style = props.style.copy(bar = "rating")
        props.max = this.max / 2
        props.value = rating
    }

    private fun Switch.captureSwitch(props: WireframeProps) {
        props.type = WireframeType.INPUT
        props.inputType = InputWireframeType.TOGGLE
        props.isChecked = isChecked
        props.label = props.text
        props.text = null
    }

    private fun TextView.resolveAlignment(): Pair<String, String> =
        when (textAlignment) {
            View.TEXT_ALIGNMENT_CENTER -> {
                ALIGN_CENTER to ALIGN_CENTER
            }
            View.TEXT_ALIGNMENT_TEXT_END, View.TEXT_ALIGNMENT_VIEW_END -> {
                ALIGN_RIGHT to ALIGN_CENTER
            }
            View.TEXT_ALIGNMENT_TEXT_START, View.TEXT_ALIGNMENT_VIEW_START -> {
                ALIGN_LEFT to ALIGN_CENTER
            }
            View.TEXT_ALIGNMENT_GRAVITY -> {
                val h =
                    when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                        Gravity.START, Gravity.LEFT -> ALIGN_LEFT
                        Gravity.END, Gravity.RIGHT -> ALIGN_RIGHT
                        Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> ALIGN_CENTER
                        else -> ALIGN_LEFT
                    }
                val v =
                    when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                        Gravity.TOP -> "top"
                        Gravity.BOTTOM -> "bottom"
                        Gravity.CENTER_VERTICAL, Gravity.CENTER -> ALIGN_CENTER
                        else -> ALIGN_CENTER
                    }
                h to v
            }
            else -> {
                ALIGN_LEFT to ALIGN_CENTER
            }
        }

    // --- Masking decisions (aligned with MaskingCollector) ---

    private fun View.isExplicitlyUnmasked(): Boolean {
        if (hasPrivacyUnmaskTag()) return true
        val tagStr = (tag as? String)?.lowercase()
        if (tagStr?.contains(ReplayConstants.UNMASK_TAG) == true) return true
        val cd = contentDescription?.run { toString().lowercase() }
        if (cd?.contains(ReplayConstants.UNMASK_TAG) == true) return true
        return false
    }

    private fun View.isExplicitlyMasked(): Boolean {
        if (hasPrivacyMaskTag()) return true
        val tagStr = (tag as? String)?.lowercase()
        if (tagStr?.contains(ReplayConstants.MASK_TAG) == true) return true
        val cd = contentDescription?.run { toString().lowercase() }
        if (cd?.contains(ReplayConstants.MASK_TAG) == true) return true
        return false
    }

    private fun TextView.shouldMaskTextView(config: SessionReplayConfig): Boolean {
        if (isExplicitlyUnmasked()) return false
        if (isExplicitlyMasked()) return true
        if (isPasswordInputType(inputType)) return true
        if (this is EditText) {
            return when (config.textAndInputPrivacy) {
                TextAndInputPrivacy.MASK_ALL -> true
                TextAndInputPrivacy.MASK_ALL_INPUTS -> true
                TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> isSensitiveInputType(inputType)
            }
        }
        return when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> false
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> isSensitiveInputType(inputType)
        }
    }

    private fun Spinner.shouldMaskSpinner(config: SessionReplayConfig): Boolean {
        if (isExplicitlyUnmasked()) return false
        if (isExplicitlyMasked()) return true
        return when (config.textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_ALL -> true
            TextAndInputPrivacy.MASK_ALL_INPUTS -> true
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> false
        }
    }

    private fun ImageView.shouldMaskImage(
        config: SessionReplayConfig,
        dm: android.util.DisplayMetrics,
    ): Boolean {
        if (isExplicitlyUnmasked()) return false
        if (isExplicitlyMasked()) return true
        return config.imagePrivacy == ImagePrivacy.MASK_ALL && drawable?.shouldMaskDrawable(dm) == true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun Drawable.shouldMaskDrawable(dm: android.util.DisplayMetrics): Boolean =
        when (this) {
            is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable, is LayerDrawable -> false
            is BitmapDrawable -> bitmap.isValid()
            else -> true
        }

    private fun isPasswordInputType(inputType: Int): Boolean = InputTypeClassifier.isPasswordInputType(inputType)

    private fun isSensitiveInputType(inputType: Int): Boolean = InputTypeClassifier.isSensitiveInputType(inputType)

    // --- Drawing helpers ---

    private fun Drawable.toRGBColor(): String? =
        when (this) {
            is ColorDrawable -> {
                color.toRGBColor()
            }
            is RippleDrawable -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        this.getDrawable(0)?.toRGBColor()
                    } catch (_: Throwable) {
                        null
                    }
                } else {
                    null
                }
            }
            is InsetDrawable -> {
                drawable?.toRGBColor()
            }
            is GradientDrawable -> {
                null
            }
            else -> {
                null
            }
        }

    private fun Drawable.base64(
        width: Int,
        height: Int,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
        cloned: Boolean = false,
    ): String? {
        config.drawableConverter?.convert(this)?.let { return it.webpBase64() }
        val clonedDrawable = if (cloned) this else copy() ?: return null
        return when (clonedDrawable) {
            is BitmapDrawable -> {
                clonedDrawable.bitmap.webpBase64()
            }
            is LayerDrawable -> {
                (0 until clonedDrawable.numberOfLayers)
                    .firstOrNull { clonedDrawable.getDrawable(it) != null }
                    ?.let { clonedDrawable.getDrawable(it)?.base64(width, height, config, displayMetrics) }
            }
            is InsetDrawable -> {
                clonedDrawable.drawable?.base64(width, height, config, displayMetrics)
            }
            else -> {
                renderToBitmapBase64(clonedDrawable, width, height)
            }
        }
    }

    private fun renderToBitmapBase64(
        drawable: Drawable,
        width: Int,
        height: Int,
    ): String? =
        try {
            val bitmap = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.webpBase64().also { bitmap.recycle() }
        } catch (_: Throwable) {
            null
        }

    private fun Drawable.copy(): Drawable? = constantState?.newDrawable()

    private fun Int.toRGBColor(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and this)

    private fun String.mask(): String = "*".repeat(length)
}
