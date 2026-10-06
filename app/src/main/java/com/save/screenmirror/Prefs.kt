package com.save.screenmirror

import android.content.Context

/** Preferencias simples de la app (segundo plano, auto-transmitir). */
object Prefs {
    private const val FILE = "screenmirror_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun backgroundEnabled(c: Context): Boolean = sp(c).getBoolean("background", true)

    fun setBackgroundEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("background", value).apply()
    }

    fun autoEmit(c: Context): Boolean = sp(c).getBoolean("auto_emit", false)

    fun setAutoEmit(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("auto_emit", value).apply()
    }

    fun batteryAsked(c: Context): Boolean = sp(c).getBoolean("battery_asked", false)

    fun setBatteryAsked(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("battery_asked", value).apply()
    }
}
