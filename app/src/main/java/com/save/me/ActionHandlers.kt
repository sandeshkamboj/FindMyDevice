package com.save.me

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import androidx.core.content.ContextCompat
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

        val deviceNickname = Preferences.getNickname(context) ?: "Device"
        if (!chatId.isNullOrBlank()) {
            val ackMsg = "[$deviceNickname] Command received: $type"
            UploadManager.init(context)
            UploadManager.sendTelegramMessage(chatId, ackMsg)
        }

        if (!checkPermissions(context, type)) {
            Log.e("ActionHandlers", "Required permissions not granted for $type")
            NotificationHelper.showNotification(
                context, "Permission Error",
                "Required permissions not granted for $type. Please allow all required permissions in system settings."
            )
            if (!chatId.isNullOrBlank()) {
                UploadManager.sendTelegramMessage(chatId, "[$deviceNickname] Error: Required permissions not granted for $type.")
            }
            return
        }

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
                OverlayHelper.showSurfaceOverlay(context) { holder, overlay ->
                    if (holder == null || overlay == null) {
                        Log.e("ActionHandlers", "Overlay creation failed for photo/video.")
                        return@showSurfaceOverlay
                    }
                    // DO NOT remove overlay here! Pass it to the FGS, which will remove it when done
                    startCameraActionInvoke(context, type, camera, flash, quality, duration, chatId, holder, overlay)
                }
                return
            } else {
                if (OverlayHelper.hasOverlayPermission(context)) {
                    OverlayHelper.showViewOverlay(context) { overlayView ->
                        if (overlayView == null) {
                            Log.e("ActionHandlers", "Audio/location overlay creation failed.")
                            return@showViewOverlay
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            OverlayHelper.removeOverlay(context, overlayView)
                            startOtherActionInvoke(context, type, duration, chatId)
                        }, 400)
                    }
                    return
                }
            }
        } else {
            startCameraActionInvoke(context, type, camera, flash, quality, duration, chatId, null, null)
        }
    }

    private fun startCameraActionInvoke(
        context: Context,
        type: String,
        camera: String?,
        flash: String?,
        quality: String?,
        duration: String?,
        chatId: String?,
        surfaceHolder: SurfaceHolder?,
        overlayView: View?
    ) {
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
            surfaceHolder,
            overlayView // Pass overlayView to service so it can remove when finished!
        )
    }

    private fun startOtherActionInvoke(
        context: Context,
        type: String,
        duration: String?,
        chatId: String?
    ) {
        when (type) {
            "audio" -> {
                val durationInt = duration?.toIntOrNull() ?: 60
                ForegroundActionService.startAudioAction(
                    context,
                    JSONObject().apply {
                        put("duration", durationInt)
                    },
                    chatId,
                    null
                )
            }
            "location" -> {
                ForegroundActionService.startLocationAction(context, chatId, null)
            }
            "ring" -> {
                ForegroundActionService.startRingAction(context, JSONObject(), null)
            }
            "vibrate" -> {
                ForegroundActionService.startVibrateAction(context, JSONObject(), null)
            }
            else -> {
                NotificationHelper.showNotification(context, "Unknown Action", "Action $type is not supported.")
                Log.w("ActionHandlers", "Unknown action type: $type")
                if (!chatId.isNullOrBlank()) {
                    val deviceNickname = Preferences.getNickname(context) ?: "Device"
                    val errMsg = "[$deviceNickname] Error: Action $type is not supported."
                    UploadManager.sendTelegramMessage(chatId, errMsg)
                }
            }
        }
    }

    private fun checkPermissions(context: Context, type: String): Boolean {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        if (type == "photo" || type == "video") {
            return has(android.Manifest.permission.CAMERA) &&
                    (Build.VERSION.SDK_INT < 34 || has(android.Manifest.permission.FOREGROUND_SERVICE_CAMERA))
        }
        if (type == "audio") {
            return has(android.Manifest.permission.RECORD_AUDIO) &&
                    (Build.VERSION.SDK_INT < 34 || has(android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE))
        }
        if (type == "location") {
            return (has(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                    has(android.Manifest.permission.ACCESS_COARSE_LOCATION)) &&
                    (Build.VERSION.SDK_INT < 34 || has(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION))
        }
        return true
    }
}