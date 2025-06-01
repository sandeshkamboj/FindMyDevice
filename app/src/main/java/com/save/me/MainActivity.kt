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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.save.me.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var permissionsUiRefresh by mutableStateOf(0)
    private var showSetup by mutableStateOf(false)
    private var permissionRequestCallback: (() -> Unit)? = null

    // Launchers for special permissions/settings
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }
    private val allFilesPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }
    private val batteryPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }

    // Launcher for standard dangerous permissions
    private val standardPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
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
                val context = LocalContext.current
                var localPermissionsUiRefresh by remember { mutableStateOf(0) }

                // Observe permission changes
                LaunchedEffect(permissionsUiRefresh) {
                    localPermissionsUiRefresh++
                }

                if (showSetup) {
                    SetupScreen(
                        onSetupComplete = {
                            showSetup = false
                            permissionsUiRefresh++
                        }
                    )
                } else {
                    // Always show main screen with permissions list and actions
                    PermissionAndMainScreen(
                        activity = this@MainActivity,
                        permissionsUiRefresh = permissionsUiRefresh,
                        onSetupClick = { showSetup = true },
                        vm = vm,
                        requestStandardPermissions = { permissions, callback ->
                            permissionRequestCallback = callback
                            standardPermissionsLauncher.launch(permissions)
                        },
                        requestOverlayPermission = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            overlayPermissionLauncher.launch(intent)
                        },
                        requestAllFilesPermission = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            allFilesPermissionLauncher.launch(intent)
                        },
                        requestBatteryPermission = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            batteryPermissionLauncher.launch(intent)
                        }
                    )
                }
            }
        }
    }

    // Setup is required if nickname or token is missing
    private fun shouldShowSetup(context: Context): Boolean {
        val token = Preferences.getBotToken(context)
        val nickname = Preferences.getNickname(context)
        return token.isNullOrBlank() || nickname.isNullOrBlank()
    }
}