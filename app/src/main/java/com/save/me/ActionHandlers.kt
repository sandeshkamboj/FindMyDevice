package com.save.me

import android.content.Context
import org.json.JSONObject

object ActionHandlers {
    fun dispatch(context: Context, type: String, options: JSONObject) {
        when (type) {
            "capturePhoto" -> ForegroundActionService.startCameraAction(context, "photo", options)
            "recordVideo" -> ForegroundActionService.startCameraAction(context, "video", options)
            "recordAudio" -> ForegroundActionService.startAudioAction(context, options)
            "getLocation" -> ForegroundActionService.startLocationAction(context)
            "ring" -> ForegroundActionService.startRingAction(context, options)
            "vibrate" -> ForegroundActionService.startVibrateAction(context, options)
        }
    }
}