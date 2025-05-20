package com.save.me

import android.content.Context
import android.media.MediaPlayer
import android.os.Vibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceUtils {
    suspend fun ring(context: Context, duration: Int) = withContext(Dispatchers.IO) {
        val player = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        player?.start()
        delay(duration * 1000L)
        player?.release()
    }
    suspend fun vibrate(context: Context, duration: Int) = withContext(Dispatchers.IO) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(duration * 1000L)
    }
}