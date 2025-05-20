package com.save.me

import android.content.Intent
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        statusIcon = findViewById(R.id.status_icon)

        // Start foreground service
        val intent = Intent(this, SurveillanceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Check Supabase connection
        GlobalScope.launch(Dispatchers.Main) {
            val connected = withContext(Dispatchers.IO) { SupabaseUtils.checkConnection() }
            if (connected) {
                statusText.text = getString(R.string.connected)
                statusText.setTextColor(resources.getColor(R.color.green, null))
                statusIcon.setImageResource(android.R.drawable.presence_online)
            } else {
                statusText.text = getString(R.string.not_connected)
                statusText.setTextColor(resources.getColor(R.color.red, null))
                statusIcon.setImageResource(android.R.drawable.presence_offline)
            }
            delay(2000)
            finish() // Auto-close after 2 seconds
        }
    }
}