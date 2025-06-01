package com.save.me

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAndMainScreen(
    activity: Activity,
    permissionsUiRefresh: Int,
    onSetupClick: () -> Unit,
    vm: MainViewModel,
    requestStandardPermissions: (Array<String>, () -> Unit) -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAllFilesPermission: () -> Unit,
    requestBatteryPermission: () -> Unit
) {
    val context = activity
    val permissionStatuses = remember(permissionsUiRefresh) {
        PermissionsAndOnboarding.getAllPermissionStatuses(context)
    }
    val missingStandard = PermissionsAndOnboarding.getMissingStandardPermissions(context)
    val needsOverlay = PermissionsAndOnboarding.needsOverlay(context)
    val needsAllFiles = PermissionsAndOnboarding.needsAllFiles(context)
    val needsBattery = PermissionsAndOnboarding.needsBattery(context)
    val needsBgLocation = !PermissionsAndOnboarding.hasBackgroundLocation(context)

    var showSettingsPrompt by remember { mutableStateOf(false) }
    var settingsPromptText by remember { mutableStateOf("") }

    // Request permissions as needed
    LaunchedEffect(permissionsUiRefresh) {
        // Standard dangerous permissions first
        if (missingStandard.isNotEmpty()) {
            requestStandardPermissions(missingStandard) { }
        } else if (needsBgLocation) {
            settingsPromptText = "Please grant 'Allow all the time' location permission in settings."
            showSettingsPrompt = true
        } else if (needsOverlay) {
            settingsPromptText = "Please grant Draw Over Other Apps permission."
            showSettingsPrompt = true
        } else if (needsAllFiles) {
            settingsPromptText = "Please grant All Files Access permission."
            showSettingsPrompt = true
        } else if (needsBattery) {
            settingsPromptText = "Please disable Battery Optimization for this app."
            showSettingsPrompt = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FindMyDevice") },
                actions = {
                    IconButton(onClick = onSetupClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setup Device")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Permissions Status", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PermissionList(
                permissionStatuses = permissionStatuses,
                onRequestSpecial = { special ->
                    when (special) {
                        "Overlay (Draw over apps)" -> requestOverlayPermission()
                        "All Files Access" -> requestAllFilesPermission()
                        "Ignore Battery Optimization" -> requestBatteryPermission()
                        "Location (All the time)" -> {
                            settingsPromptText = "Please grant 'Allow all the time' location permission in settings."
                            showSettingsPrompt = true
                        }
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            MainScreen(
                onSetupClick = onSetupClick,
                vm = vm,
                realPermissions = permissionStatuses
            )
        }
        if (showSettingsPrompt) {
            AlertDialog(
                onDismissRequest = { showSettingsPrompt = false },
                title = { Text("Permission Needed") },
                text = { Text(settingsPromptText) },
                confirmButton = {
                    TextButton(onClick = {
                        PermissionsAndOnboarding.launchAppSettings(activity)
                        showSettingsPrompt = false
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsPrompt = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PermissionList(
    permissionStatuses: List<PermissionStatus>,
    onRequestSpecial: (String) -> Unit
) {
    Column {
        permissionStatuses.forEach { status ->
            PermissionItem(status, onRequestSpecial)
        }
    }
}

@Composable
fun PermissionItem(
    status: PermissionStatus,
    onRequestSpecial: (String) -> Unit
) {
    val isSpecial = status.name in listOf(
        "Overlay (Draw over apps)",
        "All Files Access",
        "Ignore Battery Optimization",
        "Location (All the time)"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Icon(
            if (status.granted) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (status.granted) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        Spacer(Modifier.width(8.dp))
        Text(status.name, modifier = Modifier.weight(1f))
        if (!status.granted && isSpecial) {
            TextButton(onClick = { onRequestSpecial(status.name) }) {
                Text("Grant")
            }
        }
    }
}