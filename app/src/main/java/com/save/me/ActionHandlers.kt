package com.save.me

import android.content.Context
import android.util.Log
import org.json.JSONObject
import com.save.me.ForegroundActionService

object ActionHandlers {
    /**
     * Dispatches remote actions based on incoming command parameters.
     */
    fun dispatch(
        context: Context,
        type: String,
        camera: String? = null,
        flash: String? = null,
        quality: String? = null,
        duration: String? = null,
        chatId: String? = null
    ) {
        Log.d("ActionHandlers", "Dispatching type=$type camera=$camera flash=$flash quality=$quality duration=$duration chatId=$chatId")
        try {
            val deviceNickname = Preferences.getNickname(context) ?: "Device"
            // Send Telegram acknowledgement before starting the action
            if (!chatId.isNullOrBlank()) {
                val ackMsg = "[$deviceNickname] Command received: $type"
                UploadManager.init(context)
                UploadManager.sendTelegramMessage(chatId, ackMsg)
            }
            when (type) {
                "photo" -> {
                    val cam = camera ?: "front"
                    val flashEnabled = flash == "true"
                    val qualityInt = quality?.filter { it.isDigit() }?.toIntOrNull() ?: 1080
                    val durationInt = duration?.toIntOrNull() ?: 0

                    ForegroundActionService.startCameraAction(
                        context,
                        type,
                        JSONObject().apply {
                            put("camera", cam)
                            put("flash", flashEnabled)
                            put("quality", qualityInt)
                            put("duration", durationInt)
                        },
                        chatId
                    )
                }
                "video" -> {
                    val cam = camera ?: "front"
                    val flashEnabled = flash == "true"
                    val qualityInt = quality?.filter { it.isDigit() }?.toIntOrNull() ?: 480
                    val durationInt = duration?.toIntOrNull() ?: 60

                    ForegroundActionService.startCameraAction(
                        context,
                        type,
                        JSONObject().apply {
                            put("camera", cam)
                            put("flash", flashEnabled)
                            put("quality", qualityInt)
                            put("duration", durationInt)
                        },
                        chatId
                    )
                }
                "audio" -> {
                    val durationInt = duration?.toIntOrNull() ?: 60
                    ForegroundActionService.startAudioAction(
                        context,
                        JSONObject().apply {
                            put("duration", durationInt)
                        },
                        chatId
                    )
                }
                "location" -> {
                    ForegroundActionService.startLocationAction(context, chatId)
                }
                "ring" -> {
                    ForegroundActionService.startRingAction(context, JSONObject())
                }
                "vibrate" -> {
                    ForegroundActionService.startVibrateAction(context, JSONObject())
                }
                else -> {
                    NotificationHelper.showNotification(context, "Unknown Action", "Action $type is not supported.")
                    Log.w("ActionHandlers", "Unknown action type: $type")
                    if (!chatId.isNullOrBlank()) {
                        val errMsg = "[$deviceNickname] Error: Action $type is not supported."
                        UploadManager.sendTelegramMessage(chatId, errMsg)
                    }
                }
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(context, "Action Error", "Failed to perform $type: ${e.localizedMessage}")
            Log.e("ActionHandlers", "Dispatch error for $type", e)
            if (!chatId.isNullOrBlank()) {
                val deviceNickname = Preferences.getNickname(context) ?: "Device"
                val errMsg = "[$deviceNickname] Error: Failed to perform $type: ${e.localizedMessage}"
                UploadManager.sendTelegramMessage(chatId, errMsg)
            }
        }
    }
}