package io.konnecttv.glowlauncher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Base64

/** No network server. Only this app or an authorized ADB shell may read/control media. */
class MediaControlProvider : ContentProvider() {
    override fun onCreate() = true
    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid != 2000 && uid != 0 && uid != Process.myUid()) throw SecurityException("Authorized ADB shell only")
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        checkCaller()
        require(arg.orEmpty().length < 100) { "Invalid media request" }
        val args = when(method) {
            "status" -> arrayOf("status")
            "volume" -> arrayOf("volume", arg.orEmpty())
            "seek" -> arrayOf("seek", *arg.orEmpty().split(':',limit=2).toTypedArray())
            else -> throw IllegalArgumentException("Unknown media request")
        }
        val identity=Binder.clearCallingIdentity()
        val result=try { MediaBridge.execute(requireNotNull(context),args).toString() }
        finally { Binder.restoreCallingIdentity(identity) }
        return Bundle().apply { putString("PLUS_MEDIA",Base64.encodeToString(result.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)) }
    }
    override fun getType(uri:Uri):String { checkCaller();return "application/json" }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor? { checkCaller();return null }
    override fun insert(uri:Uri,values:ContentValues?):Uri? { checkCaller();throw UnsupportedOperationException() }
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int { checkCaller();throw UnsupportedOperationException() }
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int { checkCaller();throw UnsupportedOperationException() }
}
