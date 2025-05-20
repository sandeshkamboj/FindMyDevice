package com.save.me

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationUtils {
    private const val CHANNEL_ID = "surveillance_channel"
    private const val CHANNEL_NAME = "Surveillance Service"
    private const val NOTIFICATION_ID = 1
    private const val TAG = "NotificationUtils"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for background surveillance"
                enableLights(true)
                lightColor = Color.BLUE
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(context: Context, content: String = "Background surveillance active"): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("FindMyDevice")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateForegroundNotification(context: Context, content: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildForegroundNotification(context, content)
        manager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground notification updated: \"$content\"")
    }

    fun resetToDefaultNotification(context: Context) {
        updateForegroundNotification(context, "Background surveillance active")
        Log.d(TAG, "Foreground notification reset to default.")
    }
}