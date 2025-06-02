package com.save.me

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.save.me.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var permissionsUiRefresh by mutableStateOf(0)
    private var showSetup by mutableStateOf(false)

    // Launchers for special permissions/settings
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }
    private val allFilesPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }
    private val batteryPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionsUiRefresh++ }

    // Launcher for a single dangerous permission
    private val singlePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionsUiRefresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSetup = shouldShowSetup(this)
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                val context = LocalContext.current

                // Force recomposition on permission change
                var localPermissionsUiRefresh by remember { mutableStateOf(0) }
                LaunchedEffect(permissionsUiRefresh) { localPermissionsUiRefresh++ }

                if (showSetup) {
                    SetupScreen(
                        onSetupComplete = {
                            showSetup = false
                            permissionsUiRefresh++ // Triggers permission check after setup
                        }
                    )
                } else {
                    PermissionAndMainScreen(
                        activity = this@MainActivity,
                        permissionsUiRefresh = permissionsUiRefresh,
                        onSetupClick = { showSetup = true },
                        vm = vm,
                        requestPermission = { permission ->
                            singlePermissionLauncher.launch(permission)
                        },
                        openAppSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
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

    private fun shouldShowSetup(context: Context): Boolean {
        val token = Preferences.getBotToken(context)
        val nickname = Preferences.getNickname(context)
        return token.isNullOrBlank() || nickname.isNullOrBlank()
    }
}