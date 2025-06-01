package com.save.me

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionsAndOnboarding {

    private val promptPermissions: Array<String>
        get() {
            val perms = mutableListOf<String>()
            perms += Manifest.permission.CAMERA
            perms += Manifest.permission.RECORD_AUDIO
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
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

    private fun needsOverlay(context: Context) = !Settings.canDrawOverlays(context)
    private fun needsBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    private fun needsAllFiles(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()
    }
    private fun needsBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasAllPermissions(context: Context): Boolean {
        val permsGranted = promptPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val specialsOk =
            !needsOverlay(context) &&
                    !needsBattery(context) &&
                    !needsAllFiles(context) &&
                    !needsBackgroundLocation(context)
        return permsGranted && specialsOk
    }

    fun showPermissionsDialog(activity: Activity, onDone: () -> Unit) {
        val missing = mutableListOf<String>()
        if (!hasPromptPermissions(activity)) missing += "Camera, Mic, Location, Media"
        if (needsOverlay(activity)) missing += "Overlay"
        if (needsBattery(activity)) missing += "Ignore Battery Optimizations"
        if (needsAllFiles(activity)) missing += "All Files Access"
        if (needsBackgroundLocation(activity)) missing += "Background Location"

        AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage(
                "The app needs the following permissions:\n\n" +
                        missing.joinToString("\n") +
                        "\n\nPlease grant them for best results."
            )
            .setPositiveButton("Grant") { _, _ ->
                requestAllPermissions(activity, onDone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasPromptPermissions(context: Context): Boolean {
        return promptPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions(activity: Activity, onDone: () -> Unit) {
        // Prompt permissions
        ActivityCompat.requestPermissions(activity, promptPermissions, 101)

        // Overlay
        if (needsOverlay(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }

        // All Files
        if (needsAllFiles(activity)) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }

        // Battery
        if (needsBattery(activity)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
        }

        // Location Always
        if (needsBackgroundLocation(activity)) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }

        onDone()
    }
}