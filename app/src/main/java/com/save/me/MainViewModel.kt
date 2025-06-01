package com.save.me

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Permission status data class
data class PermissionStatus(val name: String, val granted: Boolean)

// MainViewModel handles UI state and actions for MainScreen
class MainViewModel : ViewModel() {

    // Permissions list (example permissions, adjust as needed)
    private val _permissions = mutableStateListOf(
        PermissionStatus("Camera", false),
        PermissionStatus("Microphone", false),
        PermissionStatus("Location", false),
        PermissionStatus("Storage", false)
    )
    val permissions: List<PermissionStatus> get() = _permissions

    // Device nickname
    private val _nickname = mutableStateOf("My Device")
    val nickname: State<String> get() = _nickname

    // Bot token
    private val _botToken = mutableStateOf("12345678:ABCDEFGH")
    val botToken: State<String> get() = _botToken

    // Service active state
    private val _serviceActive = mutableStateOf(false)
    val serviceActive: State<Boolean> get() = _serviceActive

    // Recent action history
    private val _actionHistory = mutableStateListOf<RemoteAction>()
    val actionHistory: List<RemoteAction> get() = _actionHistory

    // Currently running action
    private val _actionInProgress = mutableStateOf<RemoteAction?>(null)
    val actionInProgress: State<RemoteAction?> get() = _actionInProgress

    // Error message for snackbar
    private val _actionError = mutableStateOf<String?>(null)
    val actionError: State<String?> get() = _actionError

    // Update the nickname
    fun setNickname(newNickname: String) {
        _nickname.value = newNickname
    }

    // Update the bot token
    fun setBotToken(newToken: String) {
        _botToken.value = newToken
    }

    // Simulate refreshing permissions (randomly grant/revoke for demo)
    fun refreshPermissions() {
        _permissions.forEachIndexed { idx, perm ->
            _permissions[idx] = perm.copy(granted = (0..1).random() == 1)
        }
    }

    // Start a remote action, e.g., photo, video, audio, location
    fun startRemoteAction(type: String) {
        val newAction = RemoteAction(
            type = type,
            status = "pending",
            timestamp = System.currentTimeMillis()
        )
        _actionInProgress.value = newAction
        _actionError.value = null

        // Simulate async action and completion
        viewModelScope.launch {
            // Simulate a delay and random result
            kotlinx.coroutines.delay(1200)
            val success = (0..1).random() == 1
            val completedAction = newAction.copy(
                status = if (success) "success" else "error",
                preview = if (type == "photo" && success) "Photo.jpg" else null
            )
            _actionHistory.add(0, completedAction)
            _actionInProgress.value = null
            if (!success) {
                _actionError.value = "Failed to run $type action"
            }
        }
    }

    // Clear error after snackbar is dismissed
    fun clearError() {
        _actionError.value = null
    }
}