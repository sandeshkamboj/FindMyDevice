package com.save.me

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.delay
import java.io.File

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