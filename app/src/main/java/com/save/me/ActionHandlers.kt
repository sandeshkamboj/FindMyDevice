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
        duration: String? // <--- Added duration parameter
    ) {
        when (type) {
            "photo" -> {
                CameraService.start(
                    context,
                    type = "photo",
                    camera = camera ?: "rear",
                    flash = flash == "on",
                    quality = null,
                    duration = null // duration not needed for photo
                )
            }
            "video" -> {
                val q = when (quality) {
                    "1080p" -> 1080
                    "720p" -> 720
                    "420p" -> 420
                    else -> 720
                }
                val dur = duration?.toIntOrNull() ?: 60 // Default 60s for video
                CameraService.start(
                    context,
                    type = "video",
                    camera = camera ?: "rear",
                    flash = flash == "on",
                    quality = q,
                    duration = dur
                )
            }
            "audio" -> {
                val dur = duration?.toIntOrNull() ?: 120 // Default 120s for audio
                AudioService.start(context, dur)
            }
            "location" -> {
                LocationService.start(context)
            }
            else -> Log.e("ActionHandlers", "Unknown action type: $type")
        }
    }
}