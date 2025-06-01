package com.save.me

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var botToken by remember {
        mutableStateOf(
            TextFieldValue(
                Preferences.getBotToken(context) ?: ""
            )
        )
    }
    var nickname by remember {
        mutableStateOf(
            TextFieldValue(
                Preferences.getNickname(context) ?: ""
            )
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    fun saveSetup(context: Context, token: String, nickname: String) {
        Preferences.setBotToken(context, token)
        Preferences.setNickname(context, nickname)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Setup Device", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = botToken,
            onValueChange = { botToken = it },
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Device Nickname") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (botToken.text.isBlank() || nickname.text.isBlank()) {
                    error = "Please enter both Bot Token and Nickname."
                } else {
                    saveSetup(context, botToken.text, nickname.text)
                    onSetupComplete()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Continue")
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}