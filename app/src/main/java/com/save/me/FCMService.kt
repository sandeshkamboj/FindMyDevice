package com.save.me

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCMService", "From: ${remoteMessage.from}")

        val type = remoteMessage.data["type"]       // e.g. "photo", "video", "audio", "location", "ring", "vibrate"
        val camera = remoteMessage.data["camera"]   // e.g. "front" or "rear"
        val flash = remoteMessage.data["flash"]     // e.g. "true" or "false"
        val quality = remoteMessage.data["quality"] // e.g. "420p", "720p", "1080p"
        val duration = remoteMessage.data["duration"] // in seconds or minutes as per your design
        val chatId = remoteMessage.data["chat_id"]  // The Telegram user/chat id

        if (type != null && chatId != null) {
            ActionHandlers.dispatch(
                applicationContext,
                type = type,
                camera = camera,
                flash = flash,
                quality = quality,
                duration = duration,
                chatId = chatId
            )
        } else {
            Log.d("FCMService", "Invalid or missing command data: $type $camera $flash $quality $duration $chatId")
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCMService", "Refreshed token: $token")
        // You can send the new token to your bot server if needed
    }
}