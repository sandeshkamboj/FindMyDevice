package com.save.me

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCMService", "From: ${remoteMessage.from}")

        val type = remoteMessage.data["type"]
        val camera = remoteMessage.data["camera"]
        val flash = remoteMessage.data["flash"]
        val quality = remoteMessage.data["quality"]

        if (type != null) {
            ActionHandlers.dispatch(
                applicationContext,
                type,
                camera,
                flash,
                quality
            )
        } else {
            Log.d("FCMService", "Invalid or missing command data: $type $camera $flash $quality")
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCMService", "Refreshed token: $token")
        // You can send the new token to your bot server if needed
    }
}