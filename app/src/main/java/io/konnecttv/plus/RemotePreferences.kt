package io.konnecttv.plus

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Phone-only preferences. Never exported with ADB credentials. */
object RemotePreferences {
    private lateinit var app: Context
    val haptics = MutableStateFlow(true)
    val oneHanded = MutableStateFlow(false)
    val touchpad = MutableStateFlow(true)
    fun init(context: Context) {
        app = context.applicationContext
        val p = app.getSharedPreferences("remote", Context.MODE_PRIVATE)
        haptics.value = p.getBoolean("haptics", true)
        oneHanded.value = p.getBoolean("oneHanded", false)
        touchpad.value = p.getBoolean("touchpad", true)
    }
    fun set(key: String, value: Boolean) {
        when(key) { "haptics" -> haptics.value = value; "oneHanded" -> oneHanded.value = value; "touchpad" -> touchpad.value = value; else -> error("Unknown preference") }
        app.getSharedPreferences("remote", Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
    fun feedback(success: Boolean = false) {
        if (!haptics.value || !::app.isInitialized) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java).defaultVibrator else app.getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(if (success) 45 else 12, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
