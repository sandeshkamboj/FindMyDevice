package com.save.me

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(val name: String, val granted: Boolean)

// This object manages permission logic for the app.
object PermissionsAndOnboarding {

    // List of dangerous permissions
    @JvmStatic
    fun getPromptPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        perms += Manifest.permission.CAMERA
        perms += Manifest.permission.RECORD_AUDIO
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        perms += Manifest.permission.ACCESS_COARSE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return perms.toTypedArray()
    }

    // Returns a list of PermissionStatus for display.
    fun getAllPermissionStatuses(context: Context): List<PermissionStatus> {
        val statuses = mutableListOf<PermissionStatus>()
        // Standard dangerous permissions
        for (perm in getPromptPermissions()) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                perm
            ) == PackageManager.PERMISSION_GRANTED
            statuses.add(PermissionStatus(perm, granted))
        }
        // Special permissions
        statuses.add(PermissionStatus("Overlay (Draw over apps)", !needsOverlay(context)))
        statuses.add(PermissionStatus("All Files Access", !needsAllFiles(context)))
        statuses.add(PermissionStatus("Ignore Battery Optimization", !needsBattery(context)))
        return statuses
    }

    // Returns only the missing standard dangerous permissions.
    fun getMissingStandardPermissions(context: Context): Array<String> {
        return getPromptPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun needsOverlay(context: Context): Boolean = !Settings.canDrawOverlays(context)

    fun needsBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun needsAllFiles(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()
    }

    fun hasAllPermissions(context: Context): Boolean {
        val permsGranted = getPromptPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val specialsOk =
            !needsOverlay(context) &&
                    !needsBattery(context) &&
                    !needsAllFiles(context)
        return permsGranted && specialsOk
    }
}