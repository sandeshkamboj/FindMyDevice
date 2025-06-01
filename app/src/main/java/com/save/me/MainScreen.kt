package com.save.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSetupClick: (() -> Unit)? = null,
    vm: MainViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State from ViewModel
    val permissions = vm.permissions
    val nickname by vm.nickname
    val botToken by vm.botToken
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
                title = { Text("Remote Device Control") },
                actions = {
                    IconButton(onClick = { vm.refreshPermissions() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh permissions")
                    }
                }
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
                    vm.setNickname("Device " + (1..99).random())
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Nickname")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bot Token: ${botToken.take(8)}...",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    vm.setBotToken("ABCDEFGH" + (1..999).random())
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

            // Permissions Status
            Text("Permissions:")
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                items(permissions.size) { idx ->
                    val perm = permissions[idx]
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
                    items(actionHistory.size) { idx ->
                        val act = actionHistory[idx]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = when (act.type) {
                                    "photo" -> Icons.Filled.Edit // Or a camera icon if you add it
                                    "video" -> Icons.Filled.Refresh // Or a video icon if you add it
                                    "audio" -> Icons.Outlined.CheckCircle // Or a mic icon if you add it
                                    "location" -> Icons.Outlined.Error // Or a location icon if you add it
                                    else -> Icons.Filled.Refresh
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${act.type.replaceFirstChar { it.uppercase() }} (${act.status})")
                            Spacer(Modifier.weight(1f))
                            act.preview?.let {
                                Text(
                                    "Preview: $it",
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

// Data class for action history
data class RemoteAction(
    val type: String,
    val status: String, // pending, success, error
    val timestamp: Long,
    val preview: String? = null
)