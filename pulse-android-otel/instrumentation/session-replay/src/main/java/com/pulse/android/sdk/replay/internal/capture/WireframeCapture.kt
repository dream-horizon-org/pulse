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

/**
 * View-to-wireframe capture. View-based only; no Compose wireframes.
 * Masking logic is aligned with [MaskingCollector] (same priority system).
 */
internal object WireframeCapture {

    fun toWireframe(
        view: View,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
        logger: (String) -> Unit,
    ): ReplayWireframe? {
        return view.toWireframeInternal(null, config, displayMetrics, logger)
    }

    fun themeToRGBColor(theme: android.content.res.Resources.Theme): String? {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, value, true)
        return if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            value.data.toRGBColor()
        } else null
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun View.toWireframeInternal(
        parentId: Int?,
        config: SessionReplayConfig,
        displayMetrics: android.util.DisplayMetrics,
        logger: (String) -> Unit,
    ): ReplayWireframe? {
        if (!ScreenshotCapture.isVisible(this, logger)) return null
        val viewId = System.identityHashCode(this)
        val coordinates = IntArray(2)
        coordinates[0] = if (ScreenshotCapture.isViewStateStable(this, logger)) {
            getLocationOnScreen(coordinates)
            coordinates[0]
        } else 0
        coordinates[1] = if (ScreenshotCapture.isViewStateStable(this, logger)) coordinates[1] else 0
        val dpX = (coordinates[0] / displayMetrics.density).toInt()
        val dpY = (coordinates[1] / displayMetrics.density).toInt()
        val dpWidth = (this.width / displayMetrics.density).toInt()
        val dpHeight = (this.height / displayMetrics.density).toInt()

        var base64: String? = null
        var type: String? = null
        if (id == android.R.id.statusBarBackground) type = WireframeType.STATUS_BAR
        if (id == android.R.id.navigationBarBackground) type = WireframeType.NAVIGATION_BAR

        val bgColor = background?.toRGBColor()
        val bgImage = if (bgColor == null) background?.base64(this.width, this.height, config, displayMetrics) else null
        var style = ReplayStyle(backgroundColor = bgColor, backgroundImage = bgImage)

        var checked: Boolean? = null
        var text: String? = null
        var inputType: String? = null
        var value: Any? = null

        if (this is TextView) {
            val viewText = this.text?.toString()
            if (!viewText.isNullOrEmpty()) {
                text = if (!shouldMaskTextView(config)) viewText else viewText.mask()
            }
            val hint = this.hint?.toString()
            if (text.isNullOrEmpty() && !hint.isNullOrEmpty()) {
                text = if (!shouldMaskTextView(config)) hint else hint.mask()
            }
            type = WireframeType.TEXT

            val fontFamilyStr = when (typeface) {
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

            style = style.copy(
                color = currentTextColor.toRGBColor(),
                fontFamily = fontFamilyStr,
                fontSize = fontSizeVal,
                horizontalAlign = alignment.first,
                verticalAlign = alignment.second,
                paddingTop = if (alignment.second != "center") (totalPaddingTop / displayMetrics.density).toInt() else null,
                paddingBottom = if (alignment.second != "center") (totalPaddingBottom / displayMetrics.density).toInt() else null,
                paddingLeft = if (alignment.first != "center") (totalPaddingLeft / displayMetrics.density).toInt() else null,
                paddingRight = if (alignment.first != "center") (totalPaddingRight / displayMetrics.density).toInt() else null,
                iconLeft = iconLeft,
                iconRight = iconRight,
            )

            if (this is Button && this !is CompoundButton) {
                style = style.copy(borderWidth = 1, borderColor = "#000000")
                type = WireframeType.INPUT
                inputType = InputWireframeType.BUTTON
                value = text
                text = null
            }
        }

        var label: String? = null
        if (this is CheckBox) {
            type = WireframeType.INPUT
            inputType = InputWireframeType.CHECKBOX
            label = text
            text = null
            checked = isChecked
        }
        if (this is RadioGroup) type = WireframeType.RADIO_GROUP
        if (this is RadioButton) {
            type = WireframeType.INPUT
            inputType = InputWireframeType.RADIO
            label = text
            text = null
            checked = isChecked
        }
        if (this is EditText) {
            type = WireframeType.INPUT
            inputType = InputWireframeType.TEXT_AREA
            value = text
            text = null
        }
        var options: List<String>? = null
        if (this is Spinner) {
            type = WireframeType.INPUT
            inputType = InputWireframeType.SELECT
            val mask = shouldMaskSpinner(config)
            selectedItem?.let {
                value = if (!mask) it.toString() else it.toString().mask()
            }
            adapter?.let { adapter ->
                val items = mutableListOf<String>()
                for (i in 0 until adapter.count) {
                    adapter.getItem(i)?.toString()?.let { item ->
                        items.add(if (!mask) item else item.mask())
                    }
                }
                options = items.ifEmpty { null }
            }
        }
        if (this is ImageView) {
            type = WireframeType.IMAGE
            if (!shouldMaskImage(config, displayMetrics)) {
                drawable?.let { base64 = it.base64(width, height, config, displayMetrics) }
            }
        }
        var max: Int? = null
        if (this is ProgressBar) {
            inputType = InputWireframeType.PROGRESS
            type = WireframeType.INPUT
            if (isIndeterminate) {
                style = style.copy(bar = "circular")
            } else {
                style = style.copy(bar = "horizontal")
                max = this.max
                value = progress
            }
        }
        if (this is RatingBar) {
            style = style.copy(bar = "rating")
            max = this.max / 2
            value = rating
        }
        if (this is Switch) {
            type = WireframeType.INPUT
            inputType = InputWireframeType.TOGGLE
            checked = isChecked
            label = text
            text = null
        }
        if (this is WebView) type = WireframeType.WEB_VIEW

        val children = mutableListOf<ReplayWireframe>()
        if (this is ViewGroup && childCount > 0) {
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
            text = text,
            type = type,
            style = style,
            childWireframes = children.ifEmpty { null },
            base64 = base64,
            parentId = parentId,
            disabled = !isEnabled,
            checked = checked,
            inputType = inputType,
            value = value,
            label = label,
            options = options,
            max = max,
        )
    }

    private fun TextView.resolveAlignment(): Pair<String, String> {
        return when (textAlignment) {
            View.TEXT_ALIGNMENT_CENTER -> "center" to "center"
            View.TEXT_ALIGNMENT_TEXT_END, View.TEXT_ALIGNMENT_VIEW_END -> "right" to "center"
            View.TEXT_ALIGNMENT_TEXT_START, View.TEXT_ALIGNMENT_VIEW_START -> "left" to "center"
            View.TEXT_ALIGNMENT_GRAVITY -> {
                val h = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.START, Gravity.LEFT -> "left"
                    Gravity.END, Gravity.RIGHT -> "right"
                    Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> "center"
                    else -> "left"
                }
                val v = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                    Gravity.TOP -> "top"
                    Gravity.BOTTOM -> "bottom"
                    Gravity.CENTER_VERTICAL, Gravity.CENTER -> "center"
                    else -> "center"
                }
                h to v
            }
            else -> "left" to "center"
        }
    }

    // --- Masking decisions (aligned with MaskingCollector) ---

    private fun View.isExplicitlyUnmasked(): Boolean {
        if (hasPrivacyUnmaskTag()) return true
        val tagStr = (tag as? String)?.lowercase()
        if (tagStr?.contains(ReplayConstants.UNMASK_TAG) == true) return true
        val cd = contentDescription?.toString()?.lowercase()
        if (cd?.contains(ReplayConstants.UNMASK_TAG) == true) return true
        return false
    }

    private fun View.isExplicitlyMasked(): Boolean {
        if (hasPrivacyMaskTag()) return true
        val tagStr = (tag as? String)?.lowercase()
        if (tagStr?.contains(ReplayConstants.MASK_TAG) == true) return true
        val cd = contentDescription?.toString()?.lowercase()
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

    private fun ImageView.shouldMaskImage(config: SessionReplayConfig, dm: android.util.DisplayMetrics): Boolean {
        if (isExplicitlyUnmasked()) return false
        if (isExplicitlyMasked()) return true
        return config.imagePrivacy == ImagePrivacy.MASK_ALL && drawable?.shouldMaskDrawable(dm) == true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun Drawable.shouldMaskDrawable(dm: android.util.DisplayMetrics): Boolean = when (this) {
        is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable, is LayerDrawable -> false
        is BitmapDrawable -> bitmap.isValid()
        else -> true
    }

    private fun isPasswordInputType(inputType: Int): Boolean =
        InputTypeClassifier.isPasswordInputType(inputType)

    private fun isSensitiveInputType(inputType: Int): Boolean =
        InputTypeClassifier.isSensitiveInputType(inputType)

    // --- Drawing helpers ---

    private fun Drawable.toRGBColor(): String? = when (this) {
        is ColorDrawable -> color.toRGBColor()
        is RippleDrawable -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) try { (this as RippleDrawable).getDrawable(0)?.toRGBColor() } catch (_: Throwable) { null } else null
        is InsetDrawable -> drawable?.toRGBColor()
        is GradientDrawable -> null
        else -> null
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
        when (clonedDrawable) {
            is BitmapDrawable -> return clonedDrawable.bitmap.webpBase64()
            is LayerDrawable -> return (0 until clonedDrawable.numberOfLayers).firstOrNull { clonedDrawable.getDrawable(it) != null }?.let { clonedDrawable.getDrawable(it)?.base64(width, height, config, displayMetrics) }
            is InsetDrawable -> return clonedDrawable.drawable?.base64(width, height, config, displayMetrics)
        }
        return try {
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            clonedDrawable.setBounds(0, 0, width, height)
            clonedDrawable.draw(canvas)
            bitmap.webpBase64().also { bitmap.recycle() }
        } catch (_: Throwable) { null }
    }

    private fun Drawable.copy(): Drawable? = constantState?.newDrawable()

    private fun Int.toRGBColor(): String = String.format("#%06X", 0xFFFFFF and this)

    private fun String.mask(): String = "*".repeat(length)
}
