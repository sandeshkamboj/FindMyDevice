package com.save.me

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager

class OverlayCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    private var isAdded = false

    init {
        holder.addCallback(this)
    }

    fun addToWindow() {
        if (isAdded) return
        val params = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.x = 0
        params.y = 0

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(this, params)
        isAdded = true
    }

    fun removeFromWindow() {
        if (!isAdded) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(this)
        isAdded = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
}