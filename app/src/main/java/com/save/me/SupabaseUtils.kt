package com.save.me

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import android.content.Context
import java.io.File

object SupabaseUtils {
    private const val SUPABASE_URL = "https://yxdnyavcxouutwkvdoef.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl4ZG55YXZjeG91dXR3a3Zkb2VmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwNjE0MDQsImV4cCI6MjA2MjYzNzQwNH0.y07v2koScA07iztFr366pB5f5n5UCCzc_Agn228dujI"
    private const val EMAIL = "kambojistheking@gmail.com"       // Replace with your email
    private const val PASSWORD = "#Sand@beach1" // Replace with your password

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(io.github.jan.supabase.auth.Auth)
            install(io.github.jan.supabase.postgrest.Postgrest)
            install(io.github.jan.supabase.storage.Storage)
            install(io.github.jan.supabase.realtime.Realtime)
        }
    }

    private var userId: String? = null

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val activeSession = supabase.auth.currentSessionOrNull()
            if (activeSession == null) {
                supabase.auth.signInWith(Email) {
                    email = EMAIL
                    password = PASSWORD
                }
            }
            userId = supabase.auth.currentUserOrNull()?.id
            userId != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun uploadFileAndRecord(file: File, supaPath: String, type: String) = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext
        val bucket = "device-backups"
        try {
            supabase.storage["device-backups"].upload(supaPath, file.readBytes())
            supabase.postgrest["files"].insert(
                buildJsonObject {
                    put("user_id", uid)
                    put("type", type)
                    put("bucket", bucket)
                    put("path", supaPath)
                }
            )
        } catch (_: Exception) {}
    }

    suspend fun insertLocation(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext
        supabase.postgrest["locations"].insert(
            buildJsonObject {
                put("user_id", uid)
                put("latitude", lat)
                put("longitude", lon)
            }
        )
    }

    fun startRealtimeCommandListener(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            checkConnection()
            val uid = userId ?: return@launch
            val channel = supabase.realtime.channel("public:commands")
            // Listen for INSERT events on the commands table
            channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                table = "commands"
            }.collectLatest { change ->
                // change is PostgresAction.Insert, get record as JsonObject
                val record = change.record
                val userIdField = record["user_id"]?.jsonPrimitive?.content
                if (userIdField != uid) return@collectLatest
                val type = record["type"]?.jsonPrimitive?.content ?: return@collectLatest
                val optionsJson = record["options"]?.jsonPrimitive?.content ?: "{}"
                val options = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
                when (type) {
                    "capturePhoto" -> {
                        val camera = options.optString("camera", "back")
                        val flash = options.optString("flash", "on")
                        CoroutineScope(Dispatchers.IO).launch { CameraUtils.executeCapturePhoto(context, camera, flash) }
                    }
                    "recordVideo" -> {
                        val camera = options.optString("camera", "back")
                        val quality = options.optString("quality", "720")
                        val duration = options.optInt("duration", 60)
                        CoroutineScope(Dispatchers.IO).launch { CameraUtils.executeRecordVideo(context, camera, quality, duration) }
                    }
                    "recordAudio" -> {
                        val duration = options.optInt("duration", 60)
                        CoroutineScope(Dispatchers.IO).launch { AudioUtils.executeRecordAudio(context, duration) }
                    }
                    "ring" -> {
                        val duration = options.optInt("duration", 5)
                        CoroutineScope(Dispatchers.IO).launch { DeviceUtils.ring(context, duration) }
                    }
                    "vibrate" -> {
                        val duration = options.optInt("duration", 1)
                        CoroutineScope(Dispatchers.IO).launch { DeviceUtils.vibrate(context, duration) }
                    }
                    "getLocation" -> {
                        CoroutineScope(Dispatchers.IO).launch { LocationUtils.recordLocationOnce(context) }
                    }
                }
            }
        }
    }

    // NEW: Update the user's FCM token in the users table (add fcm_token column in Supabase)
    suspend fun updateFcmToken(token: String) = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext
        try {
            supabase.postgrest["users"].update(
                buildJsonObject {
                    put("fcm_token", token)
                }
            ) {
                filter {
                    eq("id", uid)
                }
            }
        } catch (e: Exception) {
            // Optionally: log or handle the error
        }
    }
}