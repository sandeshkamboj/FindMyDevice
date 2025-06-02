package com.save.me

import android.content.Context
import android.util.Log
import org.json.JSONObject

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
            when (type) {
                "photo", "video" -> {
                    val cam = camera ?: "rear"
                    val flashEnabled = flash == "true"
                    val qualityInt = quality?.filter { it.isDigit() }?.toIntOrNull() ?: 720
                    val durationInt = duration?.toIntOrNull() ?: if (type == "video") 60 else 0

                    Log.d("ActionHandlers", "Starting ForegroundActionService for camera: cam=$cam, flash=$flashEnabled, quality=$qualityInt, duration=$durationInt, chatId=$chatId")
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
                    Log.d("ActionHandlers", "Starting ForegroundActionService for audio: duration=$durationInt, chatId=$chatId")
                    ForegroundActionService.startAudioAction(
                        context,
                        JSONObject().apply {
                            put("duration", durationInt)
                        },
                        chatId
                    )
                }
                "location" -> {
                    Log.d("ActionHandlers", "Starting ForegroundActionService for location: chatId=$chatId")
                    ForegroundActionService.startLocationAction(context, chatId)
                }
                "ring" -> {
                    Log.d("ActionHandlers", "Starting ForegroundActionService for ring")
                    ForegroundActionService.startRingAction(context, JSONObject())
                }
                "vibrate" -> {
                    Log.d("ActionHandlers", "Starting ForegroundActionService for vibrate")
                    ForegroundActionService.startVibrateAction(context, JSONObject())
                }
                else -> {
                    NotificationHelper.showNotification(context, "Unknown Action", "Action $type is not supported.")
                    Log.w("ActionHandlers", "Unknown action type: $type")
                }
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(context, "Action Error", "Failed to perform $type: ${e.localizedMessage}")
            Log.e("ActionHandlers", "Dispatch error for $type", e)
        }
    }
}