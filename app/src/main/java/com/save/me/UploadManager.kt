package com.save.me

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.*
import java.io.File

// 1. Entity for queued uploads
@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val chatId: String,
    val type: String, // "photo", "video", "audio", "location"
    val timestamp: Long = System.currentTimeMillis()
)

// 2. DAO for pending uploads
@Dao
interface PendingUploadDao {
    @Query("SELECT * FROM pending_uploads ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingUpload>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUpload): Long

    @Delete
    suspend fun delete(upload: PendingUpload)
}

// 3. Database for Room
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

// 4. UploadManager singleton
object UploadManager {
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var dao: PendingUploadDao
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        dao = UploadDatabase.getInstance(appContext).pendingUploadDao()
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
            uploadAllPending()
        }
    }

    private suspend fun uploadAllPending() {
        val uploads = dao.getAll()
        for (upload in uploads) {
            val file = File(upload.filePath)
            if (!file.exists()) {
                dao.delete(upload)
                continue
            }
            val success = try {
                uploadFileToServer(file, upload.chatId, upload.type)
            } catch (e: Exception) {
                Log.e("UploadManager", "Upload error: ${e.localizedMessage}")
                false
            }
            if (success) {
                file.delete()
                dao.delete(upload)
            }
        }
    }

    // Replace this with your actual upload logic (HTTP POST to bot backend)
    private fun uploadFileToServer(file: File, chatId: String, type: String): Boolean {
        // Example: Use OkHttp, Retrofit, or any HTTP client to upload file and chatId
        // Return true on success, false on failure
        // Simulate network call here for demonstration (replace in production)
        Log.d("UploadManager", "Uploading $type file ${file.name} for chatId=$chatId")
        Thread.sleep(1000) // Simulate network delay
        return true // Simulate success (replace with real upload result)
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
}