package com.save.me

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // Permissions status
    val permissions = mutableStateListOf<PermissionStatus>()

    // Action history
    val actionHistory = mutableStateListOf<RemoteAction>()

    // Currently running action
    var actionInProgress = mutableStateOf<RemoteAction?>(null)
        private set

    // Error state
    var actionError = mutableStateOf<String?>(null)
        private set

    // Service status
    var serviceActive = mutableStateOf(false)
        private set

    // Customization
    var nickname = mutableStateOf("My Device")
    var botToken = mutableStateOf("Not set")

    // Initializer
    init {
        refreshPermissions()
        checkServiceStatus()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>().applicationContext
        val pm = ctx.packageManager
        val perms = listOf(
            PermissionStatus("Camera", android.Manifest.permission.CAMERA, pm.checkPermission(android.Manifest.permission.CAMERA, ctx.packageName) == PackageManager.PERMISSION_GRANTED),
            PermissionStatus("Microphone", android.Manifest.permission.RECORD_AUDIO, pm.checkPermission(android.Manifest.permission.RECORD_AUDIO, ctx.packageName) == PackageManager.PERMISSION_GRANTED),
            PermissionStatus("Location", android.Manifest.permission.ACCESS_FINE_LOCATION, pm.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, ctx.packageName) == PackageManager.PERMISSION_GRANTED),
            PermissionStatus("Storage", android.Manifest.permission.READ_EXTERNAL_STORAGE, pm.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, ctx.packageName) == PackageManager.PERMISSION_GRANTED),
            PermissionStatus("Overlay", "OVERLAY", PermissionsAndOnboarding.hasAllPermissions(ctx)), // Simplified for overlay
            PermissionStatus("Battery", "BATTERY", PermissionsAndOnboarding.hasAllPermissions(ctx)) // Simplified for battery
        )
        permissions.clear()
        permissions.addAll(perms)
    }

    fun checkServiceStatus() {
        // You can check if your ForegroundActionService, CameraService, etc. are running and update status
        // For demo, assume always active
        serviceActive.value = true
    }

    fun setNickname(newName: String) {
        nickname.value = newName
    }

    fun setBotToken(newToken: String) {
        botToken.value = newToken
    }

    fun startRemoteAction(type: String, details: String = "") {
        val action = RemoteAction(type, "pending", System.currentTimeMillis(), details)
        actionInProgress.value = action
    }

    fun completeRemoteAction(type: String, preview: String? = null) {
        val action = RemoteAction(type, "success", System.currentTimeMillis(), preview)
        actionHistory.add(0, action)
        if (actionHistory.size > 10) actionHistory.removeLast()
        actionInProgress.value = null
    }

    fun failRemoteAction(type: String, error: String) {
        actionInProgress.value = null
        actionError.value = error
    }

    fun clearError() {
        actionError.value = null
    }
}

data class PermissionStatus(val name: String, val permission: String, val granted: Boolean)