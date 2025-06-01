package com.save.me

import android.content.Context
import android.util.Log

object ActionHandlers {
    fun dispatch(
        context: Context,
        type: String,
        camera: String?,
        flash: String?,
        quality: String?
    ) {
        when (type) {
            "photo" -> {
                val cam = camera ?: CameraService.REAR_CAMERA
                val fl = flash == "on"
                CameraService.start(context, cam, CameraService.PHOTO, fl, null)
            }
            "video" -> {
                val cam = camera ?: CameraService.REAR_CAMERA
                val fl = flash == "on"
                val videoQuality = when (quality) {
                    "1080p" -> CameraService.QUALITY_1080P
                    "720p" -> CameraService.QUALITY_720P
                    "420p" -> CameraService.QUALITY_420P
                    else -> CameraService.QUALITY_720P
                }
                CameraService.start(context, cam, CameraService.VIDEO, fl, videoQuality)
            }
            "location" -> {
                LocationService.start(context)
            }
            "audio" -> {
                AudioService.start(context)
            }
            else -> Log.e("ActionHandlers", "Unknown action type: $type")
        }
    }
}