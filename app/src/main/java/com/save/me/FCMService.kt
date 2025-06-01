package com.save.me

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCMService", "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        val type = data["type"]
        val camera = data["camera"]
        val flash = data["flash"]
        val quality = data["quality"]
        val duration = data["duration"]

        if (type != null) {
            ActionHandlers.dispatch(
                applicationContext,
                type,
                camera,
                flash,
                quality,
                duration // <--- Now correctly passes duration
            )
        } else {
            Log.d("FCMService", "Invalid or missing command data: $type $camera $flash $quality $duration")
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCMService", "Refreshed token: $token")
        // You can send the new token to your bot server if needed
    }
}