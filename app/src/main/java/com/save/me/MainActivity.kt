package com.save.me

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var permissionStatusText: TextView
    private lateinit var serviceButton: Button

    private var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        permissionStatusText = findViewById(R.id.permissionStatusText)
        serviceButton = findViewById(R.id.serviceButton)

        // Onboarding/Permissions
        checkAndRequestPermissions()

        // Service button
        serviceButton.setOnClickListener {
            if (serviceRunning) {
                ForegroundActionService.stop(this)
            } else {
                ForegroundActionService.start(this)
            }
            updateServiceButton()
        }

        // Supabase connection
        lifecycleScope.launch(Dispatchers.IO) {
            SupabaseRemoteControl.ensureLoginAndListener(applicationContext)
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateServiceButton()
        checkAndRequestPermissions()
    }

    private fun updateStatus() {
        runOnUiThread {
            statusText.text = if (SupabaseRemoteControl.isConnected()) "Connected to Supabase" else "Not connected"
        }
    }

    private fun updateServiceButton() {
        serviceRunning = ForegroundActionService.isRunning(this)
        serviceButton.text = if (serviceRunning) "Stop Service" else "Start Service"
    }

    private fun checkAndRequestPermissions() {
        val context = this
        if (!PermissionsAndOnboarding.hasAllPermissions(context)) {
            PermissionsAndOnboarding.showPermissionsDialog(this) {
                permissionStatusText.text = if (PermissionsAndOnboarding.hasAllPermissions(context)) "All permissions granted" else "Missing permissions!"
            }
        } else {
            permissionStatusText.text = "All permissions granted"
        }
    }
}