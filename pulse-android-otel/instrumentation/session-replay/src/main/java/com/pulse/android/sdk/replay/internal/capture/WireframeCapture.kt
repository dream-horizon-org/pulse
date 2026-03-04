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
import com.pulse.android.sdk.replay.ReplayConstants
import com.pulse.android.sdk.replay.SessionReplayConfig
import com.pulse.android.sdk.replay.events.ReplayStyle
import com.pulse.android.sdk.replay.events.ReplayWireframe
import com.pulse.android.sdk.replay.internal.util.webpBase64
import com.pulse.android.sdk.replay.internal.util.isValid

/**
 * View-to-wireframe capture (mirrors PostHog View.toWireframe). View-based only; no Compose wireframes.
 */
internal object WireframeCapture {

    private val passwordInputTypes = listOf(
        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    )

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
        val x = (coordinates[0] / displayMetrics.density).toInt()
        val y = (coordinates[1] / displayMetrics.density).toInt()
        val width = (this.width / displayMetrics.density).toInt()
        val height = (this.height / displayMetrics.density).toInt()
        var base64: String? = null
        var type: String? = null
        if (id == android.R.id.statusBarBackground) type = "status_bar"
        if (id == android.R.id.navigationBarBackground) type = "navigation_bar"
        val style = ReplayStyle()
        background?.let { bg ->
            bg.toRGBColor()?.let { style.backgroundColor = it }
                ?: run { style.backgroundImage = bg.base64(this.width, this.height, config, displayMetrics) }
        }
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
            type = "text"
            style.color = currentTextColor.toRGBColor()
            if (this is Button && this !is CompoundButton) {
                style.borderWidth = 1
                style.borderColor = "#000000"
                type = "input"
                inputType = "button"
                value = text
                text = null
            }
            typeface?.let {
                when (it) {
                    Typeface.DEFAULT -> style.fontFamily = "sans-serif"
                    Typeface.DEFAULT_BOLD -> style.fontFamily = "sans-serif-bold"
                    Typeface.MONOSPACE -> style.fontFamily = "monospace"
                    Typeface.SERIF -> style.fontFamily = "serif"
                    else -> {}
                }
            }
            style.fontSize = (textSize / displayMetrics.density).toInt()
            when (textAlignment) {
                View.TEXT_ALIGNMENT_CENTER -> { style.verticalAlign = "center"; style.horizontalAlign = "center" }
                View.TEXT_ALIGNMENT_TEXT_END, View.TEXT_ALIGNMENT_VIEW_END -> { style.verticalAlign = "center"; style.horizontalAlign = "right" }
                View.TEXT_ALIGNMENT_TEXT_START, View.TEXT_ALIGNMENT_VIEW_START -> { style.verticalAlign = "center"; style.horizontalAlign = "left" }
                View.TEXT_ALIGNMENT_GRAVITY -> {
                    style.horizontalAlign = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                        Gravity.START, Gravity.LEFT -> "left"
                        Gravity.END, Gravity.RIGHT -> "right"
                        Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> "center"
                        else -> "left"
                    }
                    style.verticalAlign = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                        Gravity.TOP -> "top"
                        Gravity.BOTTOM -> "bottom"
                        Gravity.CENTER_VERTICAL, Gravity.CENTER -> "center"
                        else -> "center"
                    }
                }
                else -> { style.verticalAlign = "center"; style.horizontalAlign = "left" }
            }
            compoundDrawables.forEachIndexed { index, drawable ->
                drawable?.let {
                    val b64 = it.base64(this.width, this.height, config, displayMetrics)
                    when (index) {
                        0 -> style.iconLeft = b64
                        2 -> style.iconRight = b64
                        else -> {}
                    }
                }
            }
            if (style.verticalAlign != "center") {
                style.paddingTop = (totalPaddingTop / displayMetrics.density).toInt()
                style.paddingBottom = (totalPaddingBottom / displayMetrics.density).toInt()
            }
            if (style.horizontalAlign != "center") {
                style.paddingLeft = (totalPaddingLeft / displayMetrics.density).toInt()
                style.paddingRight = (totalPaddingRight / displayMetrics.density).toInt()
            }
        }
        var label: String? = null
        if (this is CheckBox) {
            type = "input"
            inputType = "checkbox"
            label = text
            text = null
            checked = isChecked
        }
        if (this is RadioGroup) type = "radio_group"
        if (this is RadioButton) {
            type = "input"
            inputType = "radio"
            label = text
            text = null
            checked = isChecked
        }
        if (this is EditText) {
            type = "input"
            inputType = "text_area"
            value = text
            text = null
        }
        var options: List<String>? = null
        if (this is Spinner) {
            type = "input"
            inputType = "select"
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
            type = "image"
            if (!shouldMaskImage(config, displayMetrics)) {
                drawable?.let { base64 = it.base64(width, height, config, displayMetrics) }
            }
        }
        var max: Int? = null
        if (this is ProgressBar) {
            inputType = "progress"
            type = "input"
            style.bar = if (isIndeterminate) "circular" else "horizontal".also { max = this.max; value = progress }
        }
        if (this is RatingBar) {
            style.bar = "rating"
            max = this.max / 2
            value = rating
        }
        if (this is Switch) {
            type = "input"
            inputType = "toggle"
            checked = isChecked
            label = text
            text = null
        }
        if (this is WebView) type = "web_view"
        val children = mutableListOf<ReplayWireframe>()
        if (this is ViewGroup && childCount > 0) {
            for (i in 0 until childCount) {
                getChildAt(i)?.toWireframeInternal(viewId, config, displayMetrics, logger)?.let { children.add(it) }
            }
        }
        return ReplayWireframe(
            id = viewId,
            x = x,
            y = y,
            width = width,
            height = height,
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

    private fun View.isNoCapture(config: SessionReplayConfig): Boolean =
        config.maskAllTextInputs || (tag as? String)?.lowercase()?.contains(ReplayConstants.MASK_TAG) == true ||
            contentDescription?.toString()?.lowercase()?.contains(ReplayConstants.MASK_TAG) == true

    private fun TextView.shouldMaskTextView(config: SessionReplayConfig): Boolean =
        isNoCapture(config) || passwordInputTypes.contains(inputType - 1)

    private fun Spinner.shouldMaskSpinner(config: SessionReplayConfig): Boolean = isNoCapture(config)

    private fun ImageView.shouldMaskImage(config: SessionReplayConfig, dm: android.util.DisplayMetrics): Boolean =
        isNoCapture(config) && drawable?.shouldMaskDrawable(dm) == true

    @Suppress("UNUSED_PARAMETER")
    private fun Drawable.shouldMaskDrawable(dm: android.util.DisplayMetrics): Boolean = when (this) {
        is InsetDrawable, is ColorDrawable, is VectorDrawable, is GradientDrawable, is LayerDrawable -> false
        is BitmapDrawable -> bitmap.isValid()
        else -> true
    }

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
        var clonedDrawable = if (cloned) this else copy() ?: return null
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
