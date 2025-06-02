package com.save.me

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.location.Location
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object CameraBackgroundHelper {

    suspend fun takePhotoOrVideo(
        context: Context,
        surfaceHolder: SurfaceHolder,
        type: String,
        cameraFacing: String,
        flash: Boolean = false,
        videoQuality: Int = 720,
        durationSec: Int = 60,
        file: File
    ): Boolean = withContext(Dispatchers.Main) {
        if (type == "photo") {
            takePhoto(context, surfaceHolder, cameraFacing, flash, file)
        } else {
            recordVideo(context, surfaceHolder, cameraFacing, flash, videoQuality, durationSec, file)
        }
    }

    private fun getCameraId(cameraManager: CameraManager, facing: String): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facingValue = chars.get(CameraCharacteristics.LENS_FACING)
            if ((facing == "front" && facingValue == CameraCharacteristics.LENS_FACING_FRONT) ||
                (facing == "rear" && facingValue == CameraCharacteristics.LENS_FACING_BACK)
            ) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    suspend fun takePhoto(
        context: Context,
        surfaceHolder: SurfaceHolder,
        cameraFacing: String,
        flash: Boolean,
        file: File
    ): Boolean = withContext(Dispatchers.Main) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = getCameraId(cameraManager, cameraFacing) ?: return@withContext false
        var cameraDevice: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var imageReader: android.media.ImageReader? = null
        val handlerThread = HandlerThread("photo_thread").apply { start() }
        val handler = Handler(handlerThread.looper)
        val future = CompletableDeferred<Boolean>()

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    val width = 1280 // Bigger preview for better capture (was 640)
                    val height = 720
                    imageReader = android.media.ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
                    val targets = listOf(surfaceHolder.surface, imageReader!!.surface)
                    device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(sess: CameraCaptureSession) {
                            session = sess
                            // 1. Start preview (required for correct pipeline)
                            val previewRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            previewRequest.addTarget(surfaceHolder.surface)
                            sess.setRepeatingRequest(previewRequest.build(), null, handler)

                            // 2. Wait 400ms to let the preview pipeline warm up
                            handler.postDelayed({
                                // 3. Build still capture request
                                val stillRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                stillRequest.addTarget(imageReader!!.surface)
                                if (flash && cameraFacing == "rear") {
                                    stillRequest.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                                }
                                // 4. Capture the photo
                                sess.capture(stillRequest.build(), object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        s: CameraCaptureSession,
                                        req: CaptureRequest,
                                        result: TotalCaptureResult
                                    ) {
                                        try {
                                            val img = imageReader!!.acquireLatestImage()
                                            val buffer = img.planes[0].buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            file.writeBytes(bytes)
                                            img.close()
                                            future.complete(true)
                                        } catch (e: Exception) {
                                            future.complete(false)
                                        }
                                        try { s.close() } catch (_: Exception) {}
                                        try { device.close() } catch (_: Exception) {}
                                        try { imageReader?.close() } catch (_: Exception) {}
                                        handlerThread.quitSafely()
                                    }
                                }, handler)
                            }, 400) // Wait at least 400ms
                        }
                        override fun onConfigureFailed(sess: CameraCaptureSession) { future.complete(false) }
                    }, handler)
                }
                override fun onDisconnected(device: CameraDevice) { future.complete(false) }
                override fun onError(device: CameraDevice, error: Int) { future.complete(false) }
            }, handler)
            future.await()
        } catch (e: Exception) {
            Log.e("CameraBackgroundHelper", "Photo error: $e")
            false
        } finally {
            try { cameraDevice?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            handlerThread.quitSafely()
        }
    }

    suspend fun recordVideo(
        context: Context,
        surfaceHolder: SurfaceHolder,
        cameraFacing: String,
        flash: Boolean,
        videoQuality: Int,
        durationSec: Int,
        file: File
    ): Boolean = withContext(Dispatchers.Main) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = getCameraId(cameraManager, cameraFacing) ?: return@withContext false
        var cameraDevice: CameraDevice? = null
        var session: CameraCaptureSession? = null
        val handlerThread = HandlerThread("video_thread").apply { start() }
        val handler = Handler(handlerThread.looper)

        val (width, height) = when (videoQuality) {
            1080 -> 1920 to 1080
            480 -> 640 to 480
            else -> 1280 to 720
        }

        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoFrameRate(30)
            setVideoSize(width, height)
            prepare()
        }
        val future = CompletableDeferred<Boolean>()
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    val targets = listOf(surfaceHolder.surface, recorder.surface)
                    device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(sess: CameraCaptureSession) {
                            session = sess
                            val capture = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            capture.addTarget(recorder.surface)
                            if (flash && cameraFacing == "rear") {
                                capture.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            }
                            sess.setRepeatingRequest(capture.build(), null, handler)
                            recorder.start()
                            handler.postDelayed({
                                try {
                                    recorder.stop()
                                    recorder.release()
                                    sess.stopRepeating()
                                    future.complete(true)
                                } catch (e: Exception) {
                                    future.complete(false)
                                }
                                try { sess.close() } catch (_: Exception) {}
                                try { device.close() } catch (_: Exception) {}
                                handlerThread.quitSafely()
                            }, (durationSec * 1000).toLong())
                        }
                        override fun onConfigureFailed(sess: CameraCaptureSession) { future.complete(false) }
                    }, handler)
                }
                override fun onDisconnected(device: CameraDevice) { future.complete(false) }
                override fun onError(device: CameraDevice, error: Int) { future.complete(false) }
            }, handler)
            future.await()
        } catch (e: Exception) {
            Log.e("CameraBackgroundHelper", "Video error: $e")
            false
        } finally {
            try { cameraDevice?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { recorder.release() } catch (_: Exception) {}
            handlerThread.quitSafely()
        }
    }
}

object AudioBackgroundHelper {
    suspend fun recordAudio(context: Context, outputFile: File, durationSec: Int) {
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        delay(durationSec * 1000L)
        recorder.stop()
        recorder.release()
    }
}

object LocationBackgroundHelper {
    suspend fun getLastLocation(context: Context, timeoutMillis: Long = 10000L): Location? {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        try {
            val lastKnown = suspendCoroutine<Location?> { cont ->
                fused.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (lastKnown != null) return lastKnown
        } catch (_: Exception) {}

        return try {
            suspendCoroutine<Location?> { cont ->
                val request = LocationRequest
                    .Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setMaxUpdates(1)
                    .setDurationMillis(timeoutMillis)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        cont.resume(result.lastLocation)
                        fused.removeLocationUpdates(this)
                    }
                    override fun onLocationAvailability(availability: LocationAvailability) {
                        if (!availability.isLocationAvailable) {
                            cont.resume(null)
                            fused.removeLocationUpdates(this)
                        }
                    }
                }
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        cont.resume(null)
                        fused.removeLocationUpdates(callback)
                    } catch (_: Exception) {}
                }, timeoutMillis)
            }
        } catch (e: Exception) {
            Log.e("LocationBackgroundHelper", "Location update failed: $e")
            null
        }
    }
}