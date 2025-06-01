package com.save.me

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.save.me.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var showSetup by remember { mutableStateOf(shouldShowSetup(this)) }
                var permissionsUiRefresh by remember { mutableStateOf(0) }
                val vm: MainViewModel = viewModel()

                // Always get live permission state
                val realPermissions = PermissionsAndOnboarding.getAllPermissionStatuses(this)

                // If setup is needed, show SetupScreen and block the rest
                if (showSetup) {
                    SetupScreen(
                        onSetupComplete = {
                            showSetup = false
                            permissionsUiRefresh++ // trigger recomposition
                        }
                    )
                } else {
                    // If permissions missing, show permissions dialog and block the rest
                    if (!PermissionsAndOnboarding.hasAllPermissions(this)) {
                        PermissionsAndOnboarding.showPermissionsDialog(
                            this
                        ) {
                            permissionsUiRefresh++
                            if (PermissionsAndOnboarding.hasAllPermissions(this)) {
                                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }

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

    // Check if we need to show setup (token/nickname missing)
    private fun shouldShowSetup(context: android.content.Context): Boolean {
        val token = Preferences.getBotToken(context)
        val nickname = Preferences.getNickname(context)
        return token.isNullOrBlank() || nickname.isNullOrBlank()
    }
}