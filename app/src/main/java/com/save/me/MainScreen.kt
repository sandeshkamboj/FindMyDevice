package com.save.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSetupClick: (() -> Unit)? = null,
    vm: MainViewModel = viewModel(),
    realPermissions: List<PermissionStatus>
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Destructure Compose states with 'by', so use them directly (without .value)
    val nickname = vm.getCurrentNickname()
    val botToken = vm.getCurrentBotToken()
    val serviceActive by vm.serviceActive
    val actionHistory = vm.actionHistory
    val actionInProgress by vm.actionInProgress
    val actionError by vm.actionError

    // FCM and Telegram status states (local mutable state)
    var fcmStatus by remember { mutableStateOf(false) }
    var fcmStatusMessage by remember { mutableStateOf<String?>(null) }
    var telegramStatus by remember { mutableStateOf(false) }
    var telegramStatusMessage by remember { mutableStateOf<String?>(null) }

    // FCM Status: get actual token from FirebaseMessaging
    LaunchedEffect(Unit) {
        fcmStatus = false
        fcmStatusMessage = null
        try {
            val token = withContext(Dispatchers.IO) {
                FirebaseMessaging.getInstance().token.await()
            }
            fcmStatus = token.isNotBlank()
            fcmStatusMessage = if (fcmStatus) "OK" else "Token missing"
        } catch (e: Exception) {
            fcmStatus = false
            fcmStatusMessage = e.localizedMessage ?: "FCM unavailable"
        }
    }

    // Telegram Bot API Status /getMe
    LaunchedEffect(botToken) {
        telegramStatus = false
        telegramStatusMessage = null
        if (botToken.isNotBlank()) {
            scope.launch {
                val (ok, msg) = checkTelegramBotApi(botToken)
                telegramStatus = ok
                telegramStatusMessage = msg
            }
        }
    }

    // Ensure service status is refreshed when screen is shown or after actions
    LaunchedEffect(actionInProgress) {
        vm.refreshServiceStatus()
    }

    // Show error as snackbar
    LaunchedEffect(actionError) {
        actionError?.let { errorMsg ->
            scope.launch {
                val res = snackbarHostState.showSnackbar(errorMsg, actionLabel = "Retry")
                if (res == SnackbarResult.ActionPerformed) {
                    actionInProgress?.type?.let { vm.startRemoteAction(it) }
                }
                vm.clearError()
            }
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
                .verticalScroll(rememberScrollState())
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

            // Connection Status Section
            Text("Connection Status:", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusDot(fcmStatus)
                Spacer(Modifier.width(8.dp))
                Text("FCM: ${if (fcmStatus) "Connected" else "Not Connected"}")
                fcmStatusMessage?.let { msg ->
                    Text(" ($msg)", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusDot(telegramStatus)
                Spacer(Modifier.width(8.dp))
                Text("Telegram Bot API: ${if (telegramStatus) "Connected" else "Not Connected"}")
                telegramStatusMessage?.let { msg ->
                    Text(" ($msg)", color = Color.Gray, fontSize = MaterialTheme.typography.bodySmall.fontSize)
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
                Spacer(Modifier.height(20.dp))
            }

            // Recent actions
            Text("Recent Remote Actions:", fontWeight = FontWeight.SemiBold)
            if (actionHistory.isEmpty()) {
                Text("No actions yet.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
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

@Composable
fun StatusDot(connected: Boolean) {
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (connected) Color(0xFF4CAF50) else Color(0xFFF44336))
    )
}

// Helper: Check Telegram Bot API connection using getMe
suspend fun checkTelegramBotApi(token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.telegram.org/bot${token}/getMe")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        if (code == 200 && text.contains("\"ok\":true")) {
            return@withContext true to "OK"
        } else {
            return@withContext false to "API Error"
        }
    } catch (e: Exception) {
        return@withContext false to (e.localizedMessage ?: "Network error")
    }
}