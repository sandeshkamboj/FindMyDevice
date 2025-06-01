package com.save.me

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionsAndOnboarding {

    // List of dangerous permissions
    private val promptPermissions: Array<String>
        get() {
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

    private fun needsOverlay(context: Context) = !Settings.canDrawOverlays(context)
    private fun needsBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    private fun needsAllFiles(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()
    }

    fun hasAllPermissions(context: Context): Boolean {
        val permsGranted = promptPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val specialsOk =
            !needsOverlay(context) &&
                    !needsBattery(context) &&
                    !needsAllFiles(context)
        return permsGranted && specialsOk
    }

    fun requestDangerousPermissions(activity: Activity, onDone: () -> Unit) {
        // Only prompt for permissions not already granted
        val toRequest = promptPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, toRequest.toTypedArray(), 101)
        } else {
            onDone()
        }
    }

    fun showPermissionsDialog(activity: Activity, onDone: () -> Unit) {
        val missing = mutableListOf<String>()
        if (!hasPromptPermissions(activity)) missing += "Camera, Mic, Location, Media"
        if (needsOverlay(activity)) missing += "Overlay (Display over other apps)"
        if (needsBattery(activity)) missing += "Ignore Battery Optimizations"
        if (needsAllFiles(activity)) missing += "All Files Access"

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.permission_required))
            .setMessage(activity.getString(R.string.permissions_needed, missing.joinToString("\n")))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.grant_permissions)) { _, _ ->
                requestDangerousPermissions(activity, onDone)
                // Special permissions are handled via system intents
                if (needsOverlay(activity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                if (needsAllFiles(activity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                }
                if (needsBattery(activity)) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                }
            }
            .setNegativeButton(activity.getString(R.string.exit)) { _, _ ->
                activity.finish()
            }
            .show()
    }

    private fun hasPromptPermissions(context: Context): Boolean {
        return promptPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}