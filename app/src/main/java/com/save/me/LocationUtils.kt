package com.save.me

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocationUtils {
    suspend fun scheduleLocationTasks(context: Context, intervalMillis: Long = 15 * 60 * 1000L) {
        while (true) {
            recordLocationOnce(context)
            delay(intervalMillis)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordLocationOnce(context: Context) = withContext(Dispatchers.IO) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val location = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
            fusedLocationClient.lastLocation.addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }
        location?.let {
            SupabaseUtils.insertLocation(it.latitude, it.longitude)
        }
    }
}