package com.save.me

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

// ==== Permissions ====
data class PermissionStatus(val name: String, val granted: Boolean)

object PermissionsAndOnboarding {

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

    fun getAllPermissionStatuses(context: Context): List<PermissionStatus> {
        val statuses = mutableListOf<PermissionStatus>()
        for (perm in getPromptPermissions()) {
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            statuses.add(PermissionStatus(perm, granted))
        }
        statuses.add(PermissionStatus("Overlay (Draw over apps)", !needsOverlay(context)))
        statuses.add(PermissionStatus("All Files Access", !needsAllFiles(context)))
        statuses.add(PermissionStatus("Ignore Battery Optimization", !needsBattery(context)))
        statuses.add(PermissionStatus("Location (All the time)", hasBackgroundLocation(context)))
        return statuses
    }

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
    fun hasBackgroundLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
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

// ==== Preferences ====
object Preferences {
    private const val PREF_NAME = "find_my_device_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_DEVICE_ID = "device_id"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getNickname(context: Context): String? {
        return prefs(context).getString(KEY_NICKNAME, null)
    }

    fun setNickname(context: Context, nickname: String) {
        prefs(context).edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getBotToken(context: Context): String? {
        return prefs(context).getString(KEY_BOT_TOKEN, null)
    }

    fun setBotToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_BOT_TOKEN, token).apply()
    }

    fun getDeviceId(context: Context): String? {
        return prefs(context).getString(KEY_DEVICE_ID, null)
    }

    fun setDeviceId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_DEVICE_ID, id).apply()
    }
}

// ==== Notification Helper ====
object NotificationHelper {
    const val CHANNEL_ID = "foreground_service_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}