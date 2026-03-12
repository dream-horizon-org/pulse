package com.pulse.android.sdk.replay.internal.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import com.pulse.android.sdk.replay.events.ScreenSizeInfo

@Suppress("DEPRECATION")
@androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
internal fun screenSizeApiR(context: Context): ScreenSizeInfo? {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
    val displayMetrics = context.resources.displayMetrics
    val bounds = windowManager.currentWindowMetrics.bounds
    val w = (bounds.width() / displayMetrics.density).toInt()
    val h = (bounds.height() / displayMetrics.density).toInt()
    return ScreenSizeInfo(w, h, displayMetrics.density)
}

@Suppress("DEPRECATION")
internal fun screenSize(context: Context): ScreenSizeInfo? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return screenSizeApiR(context)
    }
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
    val displayMetrics = context.resources.displayMetrics
    val size = Point()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getSize(size)
    val w = (size.x / displayMetrics.density).toInt()
    val h = (size.y / displayMetrics.density).toInt()
    return ScreenSizeInfo(w, h, displayMetrics.density)
}
