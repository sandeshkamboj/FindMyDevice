package com.save.me

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.save.me.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private var permissionsUiRefresh by mutableStateOf(0)
    private var showSetup by mutableStateOf(false)
    private var permissionRequestCallback: (() -> Unit)? = null

    // Launcher for overlay and manage all files special permissions
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionsUiRefresh++
    }

    private val allFilesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionsUiRefresh++
    }

    private val batteryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionsUiRefresh++
    }

    private val standardPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsUiRefresh++
        permissionRequestCallback?.invoke()
        permissionRequestCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSetup = shouldShowSetup(this)
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                val realPermissions =
                    PermissionsAndOnboarding.getAllPermissionStatuses(this@MainActivity)
                var localPermissionsUiRefresh by remember { mutableStateOf(0) }

                // Compose observes this variable to update UI when permissions change
                LaunchedEffect(permissionsUiRefresh) {
                    localPermissionsUiRefresh++
                }

                // Show setup screen if needed
                if (showSetup) {
                    SetupScreen(
                        onSetupComplete = {
                            showSetup = false
                            permissionsUiRefresh++
                        }
                    )
                } else {
                    // If permissions missing, request them
                    if (!PermissionsAndOnboarding.hasAllPermissions(this@MainActivity)) {
                        PermissionRequestScreen(
                            activity = this@MainActivity,
                            requestStandardPermissions = { permissions, callback ->
                                permissionRequestCallback = callback
                                standardPermissionsLauncher.launch(permissions)
                            },
                            requestOverlayPermission = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                            },
                            requestAllFilesPermission = {
                                val intent =
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:$packageName")
                                allFilesPermissionLauncher.launch(intent)
                            },
                            requestBatteryPermission = {
                                val intent =
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                batteryPermissionLauncher.launch(intent)
                            },
                            onAllGranted = {
                                permissionsUiRefresh++
                                Toast.makeText(
                                    this@MainActivity,
                                    "All permissions granted!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } else {
                        // Main app UI
                        MainScreen(
                            onSetupClick = { showSetup = true },
                            vm = vm,
                            realPermissions = realPermissions
                        )
                    }
                }
            }
        }
    }

    // Check if we need to show setup (token/nickname missing)
    private fun shouldShowSetup(context: Context): Boolean {
        val token = Preferences.getBotToken(context)
        val nickname = Preferences.getNickname(context)
        return token.isNullOrBlank() || nickname.isNullOrBlank()
    }
}

@Composable
fun PermissionRequestScreen(
    activity: Activity,
    requestStandardPermissions: (Array<String>, () -> Unit) -> Unit,
    requestOverlayPermission: () -> Unit,
    requestAllFilesPermission: () -> Unit,
    requestBatteryPermission: () -> Unit,
    onAllGranted: () -> Unit
) {
    val missingStandard = PermissionsAndOnboarding.getMissingStandardPermissions(activity)
    val needsOverlay = PermissionsAndOnboarding.needsOverlay(activity)
    val needsAllFiles = PermissionsAndOnboarding.needsAllFiles(activity)
    val needsBattery = PermissionsAndOnboarding.needsBattery(activity)

    var standardPrompted by remember { mutableStateOf(false) }
    var overlayPrompted by remember { mutableStateOf(false) }
    var allFilesPrompted by remember { mutableStateOf(false) }
    var batteryPrompted by remember { mutableStateOf(false) }

    if (missingStandard.isNotEmpty() && !standardPrompted) {
        LaunchedEffect(Unit) {
            standardPrompted = true
            requestStandardPermissions(missingStandard) { }
        }
    } else if (needsOverlay && !overlayPrompted) {
        LaunchedEffect(Unit) {
            overlayPrompted = true
            requestOverlayPermission()
        }
    } else if (needsAllFiles && !allFilesPrompted) {
        LaunchedEffect(Unit) {
            allFilesPrompted = true
            requestAllFilesPermission()
        }
    } else if (needsBattery && !batteryPrompted) {
        LaunchedEffect(Unit) {
            batteryPrompted = true
            requestBatteryPermission()
        }
    } else if (missingStandard.isEmpty() && !needsOverlay && !needsAllFiles && !needsBattery) {
        LaunchedEffect(Unit) {
            onAllGranted()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                "Please grant all required permissions to continue.\n\nIf you are stuck, grant permissions manually from settings.",
                color = androidx.compose.ui.graphics.Color.Red
            )
        }
    }
}