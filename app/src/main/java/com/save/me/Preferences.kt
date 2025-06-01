package com.save.me

import android.content.Context
import android.content.SharedPreferences

object Preferences {
    private const val PREF_NAME = "find_my_device_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_DEVICE_ID = "device_id"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getNickname(context: Context): String? {
        return prefs(context).getString(KEY_NICKNAME, null)
    }

    fun setNickname(context: Context, nickname: String) {
        prefs(context).edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getBotToken(context: Context): String? {
        return prefs(context).getString(KEY_BOT_TOKEN, null)
    }

    fun setBotToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_BOT_TOKEN, token).apply()
    }

    fun getDeviceId(context: Context): String? {
        return prefs(context).getString(KEY_DEVICE_ID, null)
    }

    fun setDeviceId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_DEVICE_ID, id).apply()
    }
}