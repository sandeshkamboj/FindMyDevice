package com.save.me

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FileTreeSyncUtils {
    suspend fun syncFileTreeToSupabase(context: Context) = withContext(Dispatchers.IO) {
        val rootDir = Environment.getExternalStorageDirectory()
        val entries = mutableListOf<Pair<String, Boolean>>() // Pair<path, is_dir>
        scanDirRecursive(rootDir, "", entries)
        val jsonArray = JSONArray()
        for ((path, isDir) in entries) {
            val obj = JSONObject()
            obj.put("path", path)
            obj.put("is_dir", isDir)
            jsonArray.put(obj)
        }
        SupabaseUtils.upsertFileTree(context, jsonArray)
    }

    private fun scanDirRecursive(dir: File, relPath: String, entries: MutableList<Pair<String, Boolean>>) {
        dir.listFiles()?.forEach { file ->
            val currentPath = if (relPath.isNotEmpty()) "$relPath/${file.name}" else file.name
            entries.add(currentPath to file.isDirectory)
            if (file.isDirectory) {
                scanDirRecursive(file, currentPath, entries)
            }
        }
    }
}