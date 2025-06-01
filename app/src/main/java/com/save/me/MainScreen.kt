package com.save.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSetupClick: (() -> Unit)? = null,
    vm: MainViewModel = viewModel(),
    realPermissions: List<PermissionStatus>
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val nickname = vm.getCurrentNickname()
    val botToken = vm.getCurrentBotToken()
    val serviceActive by vm.serviceActive
    val actionHistory = vm.actionHistory
    val actionInProgress by vm.actionInProgress
    val actionError by vm.actionError

    // Show error as snackbar
    LaunchedEffect(actionError) {
        actionError?.let {
            val res = snackbarHostState.showSnackbar(it, actionLabel = "Retry")
            if (res == SnackbarResult.ActionPerformed) {
                vm.startRemoteAction(actionInProgress?.type ?: "unknown")
            }
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FindMyDevice") },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Device Nickname & Bot Token
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Device: $nickname",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onSetupClick?.invoke()
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Nickname")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bot Token: ${if (botToken.length > 8) botToken.take(8) + "..." else botToken}",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onSetupClick?.invoke()
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit bot token")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Service Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Service Status: ")
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (serviceActive) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (serviceActive) "Active" else "Inactive",
                    color = if (serviceActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Permissions Status (REAL)
            Text("Permissions:")
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                items(realPermissions) { perm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (perm.granted) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                            contentDescription = null,
                            tint = if (perm.granted) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(perm.name)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // In-progress action
            actionInProgress?.let { action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Running: ${action.type.replaceFirstChar { it.uppercase() }}",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Recent actions
            Text("Recent Remote Actions:")
            if (actionHistory.isEmpty()) {
                Text("No actions yet.", color = Color.Gray)
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    items(actionHistory) { act ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${act.type.replaceFirstChar { it.uppercase() }} (${act.status})")
                            Spacer(Modifier.weight(1f))
                            act.preview?.let { preview ->
                                Text(
                                    "Preview: $preview",
                                    color = Color.Gray,
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}