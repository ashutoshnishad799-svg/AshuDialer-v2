package com.ashudialer.app.telecom

import android.graphics.Color
import android.os.Build
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.graphics.drawable.ColorDrawable

/**
 * Configures the in-call window for an Android 12+ frosted-wallpaper surface.
 * The window starts with the existing opaque fallback for a clean first frame;
 * after the first pre-draw it becomes transparent so the wallpaper/underlying
 * surface can be seen through the Compose glass layer. Older Android versions
 * keep the stable opaque behaviour.
 */
object InCallGlassHelper {
    private const val BLUR_RADIUS_PX = 85

    fun configure(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        )
        window.setDimAmount(0f)
        val lp = window.attributes
        lp.blurBehindRadius = BLUR_RADIUS_PX
        window.attributes = lp
    }

    fun revealTransparentBackground(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var revealed = false

            override fun onPreDraw(): Boolean {
                if (!revealed) {
                    revealed = true
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }
                return true
            }
        })
    }
}
