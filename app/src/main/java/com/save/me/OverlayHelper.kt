package com.save.me

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager

object OverlayHelper {
    private var overlaySurfaceView: SurfaceView? = null

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun requestOverlayPermission(context: Context) {
        if (!hasOverlayPermission(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Shows a 1x1 transparent SurfaceView overlay and returns its SurfaceHolder.
     */
    fun showOverlayWithSurface(context: Context): SurfaceHolder? {
        if (overlaySurfaceView != null) return overlaySurfaceView?.holder
        if (!hasOverlayPermission(context)) return null

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        val surfaceView = SurfaceView(context)
        try {
            wm.addView(surfaceView, params)
            overlaySurfaceView = surfaceView
            return surfaceView.holder
        } catch (e: Exception) {
            overlaySurfaceView = null
            return null
        }
    }

    /**
     * Removes the overlay SurfaceView if it exists.
     */
    fun removeOverlay(context: Context? = null) {
        if (overlaySurfaceView != null && context != null) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(overlaySurfaceView)
            } catch (_: Exception) {}
        }
        overlaySurfaceView = null
    }
}