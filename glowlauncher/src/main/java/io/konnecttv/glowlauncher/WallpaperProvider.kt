package io.konnecttv.glowlauncher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.security.MessageDigest

/** Binary wallpaper inbox for an already-authorized ADB shell. No storage permission or network server. */
class WallpaperProvider : ContentProvider() {
    private val dir get() = requireNotNull(context).filesDir
    private val pending get() = File(dir, "wallpaper-pending.jpg")

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        // Exported so Android's `content` shell tool can stream the image. Other apps cannot use it.
        if (uid != 2000 && uid != 0 && uid != Process.myUid()) throw SecurityException("Authorized ADB shell only")
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkCaller()
        require(uri.path == "/wallpaper" && mode == "w") { "Only wallpaper upload is supported" }
        return ParcelFileDescriptor.open(pending,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        checkCaller()
        val active = File(dir, "wallpaper.jpg")
        return when (method) {
            "status" -> Bundle().apply { putString("status", "ready"); putBoolean("wallpaper", active.exists()) }
            "commit" -> {
                require(pending.length() in 1..8_388_608) { "Wallpaper upload is empty or too large" }
                require(arg?.matches(Regex("[a-f0-9]{64}")) == true) { "Wallpaper checksum is required" }
                val digest = MessageDigest.getInstance("SHA-256")
                pending.inputStream().use { input ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        digest.update(bytes, 0, n)
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) } == arg) { "Wallpaper upload was interrupted" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(pending.path, bounds)
                require(bounds.outWidth in 1..1920 && bounds.outHeight in 1..1920 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 2_073_600 && bounds.outMimeType == "image/jpeg") { "Invalid wallpaper" }
                // Same-directory rename is atomic: an interrupted upload never replaces the last good wallpaper.
                check(pending.renameTo(active)) { "Couldn't save wallpaper" }
                Bundle().apply { putString("status", "ok") }
            }
            "clear" -> {
                check(!active.exists() || active.delete()) { "Couldn't remove wallpaper" }
                pending.delete()
                Bundle().apply { putString("status", "ok") }
            }
            else -> throw IllegalArgumentException("Unknown wallpaper operation")
        }
    }

    override fun getType(uri: Uri): String { checkCaller(); return "image/jpeg" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        checkCaller(); return null
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? { checkCaller(); throw UnsupportedOperationException() }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int { checkCaller(); throw UnsupportedOperationException() }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { checkCaller(); throw UnsupportedOperationException() }
}
