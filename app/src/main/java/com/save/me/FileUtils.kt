package com.save.me

import android.content.Context
import java.io.File

object FileUtils {
    suspend fun uploadSpecificFile(context: Context, path: String) {
        val file = File(Environment.getExternalStorageDirectory(), path)
        if (file.exists() && file.isFile) {
            SupabaseUtils.uploadFileAndRecord(context, file, "files/$path", "file")
        }
    }
}