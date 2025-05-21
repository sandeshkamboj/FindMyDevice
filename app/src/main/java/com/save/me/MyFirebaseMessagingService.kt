package com.save.me

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Push received: ${remoteMessage.data}")
        // Wake up your SurveillanceService when a push is received
        val intent = Intent(this, SurveillanceService::class.java)
        startForegroundService(intent)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM token: $token")
        // You can send this token to your server if you want to send targeted pushes
    }
}