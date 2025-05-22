package com.save.me

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SupabaseUtils {
    private var client: SupabaseClient? = null

    fun getClient(context: Context): SupabaseClient {
        if (client == null) {
            client = SupabaseClient.create(
                supabaseUrl = "https://yxdnyavcxouutwkvdoef.supabase.co",
                supabaseKey = "YOUR_SUPABASE_SERVICE_KEY" // Replace with your key
            )
        }
        return client!!
    }

    suspend fun uploadFileAndRecord(context: Context, file: File, path: String, type: String) = withContext(Dispatchers.IO) {
        try {
            val supabase = getClient(context)
            supabase.storage["files"].upload(path, file)
            supabase.from("files").insert(
                mapOf(
                    "path" to path,
                    "type" to type,
                    "url" to "https://yxdnyavcxouutwkvdoef.supabase.co/storage/v1/object/public/files/$path"
                )
            )
            file.delete() // Clean up after successful upload
        } catch (e: Exception) {
            Log.e("SupabaseUtils", "Upload failed, queueing: $e")
            PendingUploadManager.queuePendingUpload(context, file.absolutePath, path, type)
            throw e
        }
    }

    suspend fun upsertFileTree(context: Context, fileTree: List<Pair<String, Boolean>>) = withContext(Dispatchers.IO) {
        val supabase = getClient(context)
        val items = fileTree.map { (path, isDir) ->
            mapOf("path" to path, "is_dir" to isDir)
        }
        supabase.from("filetree").upsert(items)
    }

    suspend fun insertLocation(context: Context, latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        getClient(context).from("locations").insert(mapOf("latitude" to latitude, "longitude" to longitude))
    }
}