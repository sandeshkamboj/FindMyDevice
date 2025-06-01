package com.save.me

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(val name: String, val granted: Boolean)

object PermissionsAndOnboarding {

    /**
     * Returns a list of standard dangerous permissions required by the app.
     */
    fun getPromptPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.READ_MEDIA_AUDIO
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return perms.toTypedArray()
    }

    /**
     * Returns a list of PermissionStatus including all dangerous and special permissions.
     */
    fun getAllPermissionStatuses(context: Context): List<PermissionStatus> {
        val statuses = mutableListOf<PermissionStatus>()
        // Standard dangerous permissions
        for (perm in getPromptPermissions()) {
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            statuses.add(PermissionStatus(perm, granted))
        }
        // Special permissions
        statuses.add(PermissionStatus("Overlay (Draw over apps)", !needsOverlay(context)))
        statuses.add(PermissionStatus("All Files Access", !needsAllFiles(context)))
        statuses.add(PermissionStatus("Ignore Battery Optimization", !needsBattery(context)))
        statuses.add(PermissionStatus("Location (All the time)", hasBackgroundLocation(context)))
        return statuses
    }

    /**
     * Returns only the missing dangerous permissions for system dialog requests.
     */
    fun getMissingStandardPermissions(context: Context): Array<String> {
        return getPromptPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    /**
     * Returns true if overlay (draw over other apps) permission is needed.
     */
    fun needsOverlay(context: Context): Boolean = !Settings.canDrawOverlays(context)

    /**
     * Returns true if battery optimization exemption is needed.
     */
    fun needsBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns true if all files access is needed (Android 11+ only).
     */
    fun needsAllFiles(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()
    }

    /**
     * Returns true if "Allow all the time" location is granted.
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // On Android 9 and below, foreground location implies background access
        }
    }

    /**
     * Returns true if all dangerous and special permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        val permsGranted = getPromptPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val specialsOk =
            !needsOverlay(context) &&
                    !needsBattery(context) &&
                    !needsAllFiles(context) &&
                    hasBackgroundLocation(context)
        return permsGranted && specialsOk
    }

    // ---- Helper functions for opening system settings for special permissions ----

    fun launchOverlaySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
        activity.startActivity(intent)
    }

    fun launchAllFilesSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.parse("package:${activity.packageName}")
        activity.startActivity(intent)
    }

    fun launchBatteryOptimizationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        activity.startActivity(intent)
    }

    fun launchAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${activity.packageName}")
        activity.startActivity(intent)
    }
}