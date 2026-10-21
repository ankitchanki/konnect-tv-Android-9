package io.konnecttv.glowlauncher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import org.json.JSONObject
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Existing ADB authorization is the only transport; no open LAN listener. */
class RemoteControlProvider : ContentProvider() {
    override fun onCreate() = true
    private fun authorize() { if(Binder.getCallingUid() !in listOf(0,2000,Process.myUid())) throw SecurityException("Authorized ADB shell only") }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        authorize()
        require(method in setOf("status","move","click","dim","message","hide") && arg.orEmpty().length <= 2048)
        val result = runCatching {
            val data = if(arg.isNullOrBlank()) JSONObject() else JSONObject(String(Base64.decode(arg,Base64.NO_WRAP),Charsets.UTF_8))
            val task = FutureTask { RemoteInputService.active?.execute(method,data)
                ?: if(method=="status") JSONObject().put("ok",true).put("available",false) else error("Enable TV touchpad and bubbles in phone Settings first.") }
            if(Looper.myLooper()==Looper.getMainLooper()) task.run() else Handler(Looper.getMainLooper()).post(task)
            task.get(3,TimeUnit.SECONDS)
        }.getOrElse { JSONObject().put("ok",false).put("error","Remote overlay unavailable; check TV accessibility access.") }
        return Bundle().apply { putString("PLUS_REMOTE",Base64.encodeToString(result.toString().toByteArray(),Base64.NO_WRAP)) }
    }
    override fun getType(uri:Uri):String { authorize(); return "application/json" }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor? { authorize();return null }
    override fun insert(uri:Uri,values:ContentValues?):Uri? { authorize();throw UnsupportedOperationException() }
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int { authorize();throw UnsupportedOperationException() }
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int { authorize();throw UnsupportedOperationException() }
}
