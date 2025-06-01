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
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// ---- NotificationHelper ----
object NotificationHelper {
    const val CHANNEL_ID = "findmydevice_channel"
    private const val CHANNEL_NAME = "FindMyDevice Notifications"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, message: String, id: Int = 1001) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }
}

// ---- Preferences ----
private const val PREFS_NAME = "findmydevice_prefs"
private const val BOT_TOKEN_KEY = "bot_token"
private const val NICKNAME_KEY = "nickname"

fun getPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun setBotToken(context: Context, token: String) =
    getPrefs(context).edit().putString(BOT_TOKEN_KEY, token).apply()

fun getBotToken(context: Context): String =
    getPrefs(context).getString(BOT_TOKEN_KEY, "") ?: ""

fun setNickname(context: Context, nickname: String) =
    getPrefs(context).edit().putString(NICKNAME_KEY, nickname).apply()

fun getNickname(context: Context): String =
    getPrefs(context).getString(NICKNAME_KEY, "") ?: ""

// ---- PermissionsAndOnboarding ----
object PermissionsAndOnboarding {
    fun getAllStandardPermissions(context: Context): List<String> {
        val perms = mutableListOf<String>()

        perms.add(Manifest.permission.CAMERA)
        perms.add(Manifest.permission.RECORD_AUDIO)
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return perms.distinct()
    }

    fun getMissingStandardPermissions(context: Context): Array<String> {
        val perms = getAllStandardPermissions(context)
        return perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun needsOverlay(context: Context): Boolean {
        return !Settings.canDrawOverlays(context)
    }

    fun needsAllFiles(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
    }

    fun needsBattery(context: Context): Boolean {
        val intent = Intent()
        val packageName = context.packageName
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:$packageName")
        // Could use PowerManager for actual check
        return false
    }

    fun hasBackgroundLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllPermissionStatuses(context: Context): List<PermissionStatus> {
        val perms = getAllStandardPermissions(context)
        val statuses = perms.map { perm ->
            PermissionStatus(
                name = when (perm) {
                    Manifest.permission.CAMERA -> "Camera"
                    Manifest.permission.RECORD_AUDIO -> "Microphone"
                    Manifest.permission.ACCESS_FINE_LOCATION -> "Location (Precise)"
                    Manifest.permission.ACCESS_COARSE_LOCATION -> "Location (Approximate)"
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Location (All the time)"
                    Manifest.permission.READ_MEDIA_IMAGES -> "Media Images"
                    Manifest.permission.READ_MEDIA_VIDEO -> "Media Video"
                    Manifest.permission.READ_MEDIA_AUDIO -> "Media Audio"
                    Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage (Read)"
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage (Write)"
                    else -> perm
                },
                granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            )
        }.toMutableList()

        statuses.add(
            PermissionStatus(
                name = "Overlay (Draw over apps)",
                granted = !needsOverlay(context)
            )
        )
        statuses.add(
            PermissionStatus(
                name = "All Files Access",
                granted = !needsAllFiles(context)
            )
        )
        statuses.add(
            PermissionStatus(
                name = "Ignore Battery Optimization",
                granted = !needsBattery(context)
            )
        )
        return statuses
    }

    fun launchAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", activity.packageName, null)
        intent.data = uri
        activity.startActivity(intent)
    }
}

data class PermissionStatus(val name: String, val granted: Boolean)