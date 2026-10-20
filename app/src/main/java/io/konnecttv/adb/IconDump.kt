package io.konnecttv.adb

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Runs on the TV, not the phone: KonnectTV copies its own APK to /data/local/tmp and starts this
 * with app_process as the shell user. The TV's own PackageManager renders each app's icon
 * (adaptive icons, split APKs and all), and we print one "package<TAB>label<TAB>base64-png" line per app.
 */
object IconDump {
    private const val SIZE = 144
    private const val DPI = DisplayMetrics.DENSITY_XXHIGH

    @JvmStatic
    fun main(args: Array<String>) {
        val pm = systemContext().packageManager
        for (pkg in args) runCatching {
            val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS) // turned-off apps too
            // PackageManager can't load other apps' resources in a bare app_process, so open the
            // app's own APKs (base + splits, where density icons often live) directly.
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val add = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).forEach { add.invoke(assets, it) }
            val metrics = DisplayMetrics().apply { setToDefaults(); densityDpi = DPI; density = DPI / 160f }
            val config = Configuration().apply { densityDpi = DPI; setLocale(Locale.getDefault()) }
            @Suppress("DEPRECATION")
            val res = Resources(assets, metrics, config)
            val label = (info.nonLocalizedLabel ?: info.labelRes.takeIf { it != 0 }?.let { runCatching { res.getText(it) }.getOrNull() })
                ?.toString()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            // No icon of its own: send the label only, and the phone shows a letter tile.
            val png = if (info.icon == 0) null else runCatching {
                val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                res.getDrawableForDensity(info.icon, DPI, null)!!.apply { setBounds(0, 0, SIZE, SIZE); draw(Canvas(bitmap)) }
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }.onFailure { System.err.println("$pkg: $it") }.getOrNull()
            println(pkg + "\t" + label + "\t" + png?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty())
        }.onFailure { System.err.println("$pkg: $it") }
        System.out.flush()
    }

    /** A Context without being an app, the way scrcpy's server gets one. */
    private fun systemContext(): Context {
        Looper.prepareMainLooper()
        val type = Class.forName("android.app.ActivityThread")
        // systemMain initializes both the system Context and Application. Avoid writing
        // mSystemThread directly: Android 16 blocks reflective access to that field.
        val thread = type.getDeclaredMethod("systemMain").invoke(null)
        return type.getDeclaredMethod("getSystemContext").invoke(thread) as Context
    }
}
