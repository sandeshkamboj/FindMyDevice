package com.save.me

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import org.json.JSONObject

object ActionHandlers {
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
            if (!chatId.isNullOrBlank()) {
                val ackMsg = "[$deviceNickname] Command received: $type"
                UploadManager.init(context)
                UploadManager.sendTelegramMessage(chatId, ackMsg)
            }

            var surfaceHolder: SurfaceHolder? = null
            if (Build.VERSION.SDK_INT >= 34) {
                if (type == "photo" || type == "video") {
                    if (!OverlayHelper.hasOverlayPermission(context)) {
                        OverlayHelper.requestOverlayPermission(context)
                        NotificationHelper.showNotification(
                            context,
                            "Permission Needed",
                            "Grant 'Display over other apps' for full background capability."
                        )
                        return
                    }
                    surfaceHolder = OverlayHelper.showOverlayWithSurface(context)
                }
            }

            when (type) {
                "photo", "video" -> {
                    val cam = camera ?: "front"
                    val flashEnabled = flash == "true"
                    val qualityInt = quality?.filter { it.isDigit() }?.toIntOrNull() ?: if (type == "photo") 1080 else 480
                    val durationInt = duration?.toIntOrNull() ?: if (type == "photo") 0 else 60

                    ForegroundActionService.startCameraAction(
                        context,
                        type,
                        JSONObject().apply {
                            put("camera", cam)
                            put("flash", flashEnabled)
                            put("quality", qualityInt)
                            put("duration", durationInt)
                        },
                        chatId,
                        surfaceHolder
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