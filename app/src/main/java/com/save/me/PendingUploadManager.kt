package com.save.me

import android.content.Context
import android.util.Log
import androidx.work.*
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

// Simple on-disk queue file for pending uploads
object PendingUploadManager {
    private const val PENDING_UPLOADS_FILE = "pending_uploads.json"

    data class PendingUpload(
        val filePath: String,
        val remotePath: String,
        val type: String
    )

    fun queuePendingUpload(context: Context, filePath: String, remotePath: String, type: String) {
        val pending = loadQueue(context).toMutableList()
        pending.add(PendingUpload(filePath, remotePath, type))
        saveQueue(context, pending)
        scheduleRetry(context)
    }

    private fun loadQueue(context: Context): List<PendingUpload> {
        return try {
            val file = File(context.filesDir, PENDING_UPLOADS_FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                PendingUpload(
                    obj.getString("filePath"),
                    obj.getString("remotePath"),
                    obj.getString("type")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveQueue(context: Context, queue: List<PendingUpload>) {
        val arr = JSONArray()
        queue.forEach {
            val obj = org.json.JSONObject()
            obj.put("filePath", it.filePath)
            obj.put("remotePath", it.remotePath)
            obj.put("type", it.type)
            arr.put(obj)
        }
        File(context.filesDir, PENDING_UPLOADS_FILE).writeText(arr.toString())
    }

    private fun scheduleRetry(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<PendingUploadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pending_upload_worker",
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    class PendingUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val ctx = applicationContext
            val queue = loadQueue(ctx).toMutableList()
            if (queue.isEmpty()) return@withContext Result.success()
            val iterator = queue.iterator()
            var allSuccess = true
            while (iterator.hasNext()) {
                val upload = iterator.next()
                val file = File(upload.filePath)
                if (!file.exists()) {
                    iterator.remove()
                    continue
                }
                try {
                    SupabaseUtils.getClient(ctx).storage["files"].upload(upload.remotePath, file)
                    SupabaseUtils.getClient(ctx).from("files").insert(
                        mapOf(
                            "path" to upload.remotePath,
                            "type" to upload.type,
                            "url" to "https://yxdnyavcxouutwkvdoef.supabase.co/storage/v1/object/public/files/${upload.remotePath}"
                        )
                    )
                    file.delete()
                    iterator.remove()
                } catch (e: Exception) {
                    Log.e("PendingUploadWorker", "Still failed: $e")
                    allSuccess = false
                    // Keep in queue for next retry
                }
            }
            saveQueue(ctx, queue)
            return@withContext if (queue.isEmpty() || allSuccess) Result.success() else Result.retry()
        }
    }

    // Call this on app start to auto-retry any pending uploads
    fun scheduleRetryOnAppStart(context: Context) {
        scheduleRetry(context)
    }
}