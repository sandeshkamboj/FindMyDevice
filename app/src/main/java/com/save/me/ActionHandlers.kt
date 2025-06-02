package com.save.me

import android.content.Context
import android.util.Log
import org.json.JSONObject

object ActionHandlers {
    /**
     * Dispatches remote actions based on incoming command parameters.
     *
     * @param context The application context.
     * @param type The action type (e.g., "photo", "video", "audio", "location", "ring", "vibrate").
     * @param camera Camera parameter, e.g., "front" or "rear".
     * @param flash Flash parameter, e.g., "true" or "false".
     * @param quality Quality parameter, e.g., "720p".
     * @param duration Duration parameter, as String (should be parsed to Int if needed).
     * @param chatId The Telegram user/chat ID.
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
                }
            }
        } catch (e: Exception) {
            NotificationHelper.showNotification(context, "Action Error", "Failed to perform $type: ${e.localizedMessage}")
            Log.e("ActionHandlers", "Dispatch error for $type", e)
        }
    }
}