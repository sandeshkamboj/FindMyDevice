package com.save.me

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException

@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val chatId: String,
    val type: String, // "photo", "video", "audio", "location", "text"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PendingUploadDao {
    @Query("SELECT * FROM pending_uploads ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingUpload>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUpload): Long

    @Delete
    suspend fun delete(upload: PendingUpload)
}

@Database(entities = [PendingUpload::class], version = 1)
abstract class UploadDatabase : RoomDatabase() {
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        @Volatile
        private var INSTANCE: UploadDatabase? = null

        fun getInstance(context: Context): UploadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UploadDatabase::class.java,
                    "upload_manager_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object UploadManager {
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var dao: PendingUploadDao
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getBotToken(): String {
        val token = Preferences.getBotToken(appContext)
        return token ?: ""
    }

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        dao = UploadDatabase.getInstance(appContext).pendingUploadDao()
        NotificationHelper.createChannel(appContext)
        setupNetworkCallback()
        scope.launch { uploadAllPending() }
        initialized = true
    }

    fun queueUpload(file: File, chatId: String, type: String) {
        scope.launch {
            val upload = PendingUpload(
                filePath = file.absolutePath,
                chatId = chatId,
                type = type
            )
            dao.insert(upload)
            showNotification("Queued: $type", "Preparing to upload ${file.name}")
            uploadAllPending()
        }
    }

    private suspend fun uploadAllPending() {
        val uploads = dao.getAll()
        for (upload in uploads) {
            val file = File(upload.filePath)
            if (!file.exists() || file.length() == 0L) {
                dao.delete(upload)
                showNotification("Skipped: ${upload.type}", "File missing: ${file.name}")
                continue
            }
            showNotification("Uploading: ${upload.type}", "Uploading ${file.name}")
            val success = try {
                if (upload.type == "location") {
                    sendLocationMessageToTelegram(file, upload.chatId)
                } else {
                    uploadFileToTelegram(file, upload.chatId, upload.type)
                }
            } catch (e: Exception) {
                Log.e("UploadManager", "Upload error: ${e.localizedMessage}")
                showNotification("Upload Error", "Error uploading ${file.name}: ${e.localizedMessage}")
                false
            }
            if (success) {
                file.delete()
                dao.delete(upload)
                showNotification("Upload Success", "${upload.type.capitalize()} uploaded: ${file.name}")
            } else {
                showNotification("Upload Failed", "${file.name} could not be uploaded.")
            }
        }
    }

    /**
     * Sends a location as a message to the Telegram bot.
     * The file should contain a Google Maps URL in plain text (as produced by LocationService).
     * If the file is not a valid maps URL, sends the raw text.
     */
    private fun sendLocationMessageToTelegram(file: File, chatId: String): Boolean {
        val botToken = getBotToken()
        if (botToken.isBlank()) {
            Log.e("UploadManager", "Bot token is missing!")
            return false
        }

        val text = try {
            file.readText().trim()
        } catch (e: Exception) {
            Log.e("UploadManager", "Failed to read location file: ${e.localizedMessage}")
            return false
        }

        // Special handling: If the text matches a Google Maps URL, try to extract lat/lng and send as real location
        val mapsRegex = Regex("""https?://maps\.google\.com/\?q=([-0-9.]+),([-0-9.]+)""")
        val match = mapsRegex.find(text)
        val url: String
        val method: String
        val body: RequestBody

        if (match != null && match.groupValues.size == 3) {
            // Send as real location using sendLocation
            val lat = match.groupValues[1]
            val lng = match.groupValues[2]
            url = "https://api.telegram.org/bot$botToken/sendLocation"
            method = "sendLocation"
            body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("latitude", lat)
                .add("longitude", lng)
                .build()
        } else {
            // Send as plain text message
            url = "https://api.telegram.org/bot$botToken/sendMessage"
            method = "sendMessage"
            body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build()
        }

        val client = OkHttpClient()
        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("UploadManager", "Telegram $method failed: ${response.code} ${response.body?.string()}")
                    return false
                }
                Log.d("UploadManager", "Telegram $method succeeded for location: $text")
                return true
            }
        } catch (e: IOException) {
            Log.e("UploadManager", "Telegram $method exception: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Uploads a file to the Telegram bot using the Telegram Bot API.
     * Supports: photo, video, audio, document (for text/errors).
     * Returns true if upload succeeded, false otherwise.
     */
    private fun uploadFileToTelegram(file: File, chatId: String, type: String): Boolean {
        val botToken = getBotToken()
        if (botToken.isBlank()) {
            Log.e("UploadManager", "Bot token is missing!")
            return false
        }

        val url = when (type) {
            "photo" -> "https://api.telegram.org/bot$botToken/sendPhoto"
            "video" -> "https://api.telegram.org/bot$botToken/sendVideo"
            "audio" -> "https://api.telegram.org/bot$botToken/sendAudio"
            "text" -> "https://api.telegram.org/bot$botToken/sendDocument"
            else -> "https://api.telegram.org/bot$botToken/sendDocument"
        }

        val client = OkHttpClient()

        val fileField = when (type) {
            "photo" -> "photo"
            "video" -> "video"
            "audio" -> "audio"
            else -> "document"
        }

        val mediaType = when (type) {
            "photo" -> "image/jpeg"
            "video" -> "video/mp4"
            "audio" -> "audio/mp4"
            else -> "application/octet-stream"
        }

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)

        requestBodyBuilder.addFormDataPart(
            fileField,
            file.name,
            file.asRequestBody(mediaType.toMediaTypeOrNull())
        )

        val requestBody = requestBodyBuilder.build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("UploadManager", "Telegram upload failed: ${response.code} ${response.body?.string()}")
                    return false
                }
                Log.d("UploadManager", "Telegram upload succeeded: ${file.name}")
                return true
            }
        } catch (e: IOException) {
            Log.e("UploadManager", "Telegram upload exception: ${e.localizedMessage}")
            return false
        }
    }

    private fun setupNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { uploadAllPending() }
                }
            })
        }
    }

    private fun showNotification(title: String, text: String) {
        NotificationHelper.showNotification(appContext, title, text, id = 2001)
    }
}