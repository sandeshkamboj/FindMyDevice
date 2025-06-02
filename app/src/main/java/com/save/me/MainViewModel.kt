package com.save.me

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

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

    // Get current nickname and bot token from Preferences
    fun getCurrentNickname(): String =
        Preferences.getNickname(getApplication()) ?: "Device"

    fun getCurrentBotToken(): String =
        Preferences.getBotToken(getApplication()) ?: "Not set"

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