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
        showDefaultNotification()

        // Listen for panel commands via Supabase
        scope.launch { SupabaseCommandListener.start(this@SurveillanceService) }
        // Periodic location update every 15 minutes
        scope.launch { LocationUtils.scheduleLocationTasks(this@SurveillanceService, 15 * 60 * 1000L) }
    }

    private fun showDefaultNotification() {
        val notification = NotificationUtils.buildForegroundNotification(this)
        startForeground(NotificationUtils.NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        SupabaseCommandListener.stop()
    }
}