package com.save.me

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionAndMainScreen(
    activity: Activity,
    permissionsUiRefresh: Int,
    onSetupClick: () -> Unit,
    vm: MainViewModel,
    requestPermission: (String) -> Unit,
    openAppSettings: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAllFilesPermission: () -> Unit,
    requestBatteryPermission: () -> Unit
) {
    val context = LocalContext.current
    var permissionStatuses by remember { mutableStateOf(PermissionsAndOnboarding.getAllPermissionStatuses(context)) }
    var needsOverlay by remember { mutableStateOf(PermissionsAndOnboarding.needsOverlay(context)) }
    var needsAllFiles by remember { mutableStateOf(PermissionsAndOnboarding.needsAllFiles(context)) }
    var needsBattery by remember { mutableStateOf(PermissionsAndOnboarding.needsBattery(context)) }
    var needsBgLocation by remember { mutableStateOf(!PermissionsAndOnboarding.hasBackgroundLocation(context)) }

    LaunchedEffect(permissionsUiRefresh) {
        permissionStatuses = PermissionsAndOnboarding.getAllPermissionStatuses(context)
        needsOverlay = PermissionsAndOnboarding.needsOverlay(context)
        needsAllFiles = PermissionsAndOnboarding.needsAllFiles(context)
        needsBattery = PermissionsAndOnboarding.needsBattery(context)
        needsBgLocation = !PermissionsAndOnboarding.hasBackgroundLocation(context)
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
                activity = activity,
                permissionStatuses = permissionStatuses,
                requestPermission = requestPermission,
                openAppSettings = openAppSettings,
                requestOverlayPermission = requestOverlayPermission,
                requestAllFilesPermission = requestAllFilesPermission,
                requestBatteryPermission = requestBatteryPermission
            )
            Spacer(Modifier.height(16.dp))
            MainScreen(
                onSetupClick = onSetupClick,
                vm = vm,
                realPermissions = permissionStatuses
            )
        }
    }
}

@Composable
fun PermissionList(
    activity: Activity,
    permissionStatuses: List<PermissionStatus>,
    requestPermission: (String) -> Unit,
    openAppSettings: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAllFilesPermission: () -> Unit,
    requestBatteryPermission: () -> Unit
) {
    Column {
        permissionStatuses.forEach { status ->
            PermissionItem(
                activity = activity,
                status = status,
                requestPermission = requestPermission,
                openAppSettings = openAppSettings,
                requestOverlayPermission = requestOverlayPermission,
                requestAllFilesPermission = requestAllFilesPermission,
                requestBatteryPermission = requestBatteryPermission
            )
        }
    }
}

@Composable
fun PermissionItem(
    activity: Activity,
    status: PermissionStatus,
    requestPermission: (String) -> Unit,
    openAppSettings: () -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAllFilesPermission: () -> Unit,
    requestBatteryPermission: () -> Unit
) {
    val isSpecial = status.name in listOf(
        "Overlay (Draw over apps)",
        "All Files Access",
        "Ignore Battery Optimization"
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
        if (!status.granted) {
            when (status.name) {
                "Overlay (Draw over apps)" -> {
                    TextButton(onClick = requestOverlayPermission) { Text("Grant") }
                }
                "All Files Access" -> {
                    TextButton(onClick = requestAllFilesPermission) { Text("Grant") }
                }
                "Ignore Battery Optimization" -> {
                    TextButton(onClick = requestBatteryPermission) { Text("Grant") }
                }
                else -> {
                    val systemPermission = PermissionsAndOnboarding.getSystemPermissionFromLabel(status.name)
                    val canRequest = systemPermission != null && PermissionsAndOnboarding.canRequestPermission(activity, systemPermission)
                    TextButton(onClick = {
                        if (canRequest && systemPermission != null) {
                            requestPermission(systemPermission)
                        } else {
                            openAppSettings()
                        }
                    }) { Text("Grant") }
                }
            }
        }
    }
}