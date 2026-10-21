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
        require(method in setOf("status","move","click","dim","message","hide","bubbleConfig","setBubbleConfig") && arg.orEmpty().length <= 65536)
        val result = runCatching {
            val data = if(arg.isNullOrBlank()) JSONObject() else JSONObject(String(Base64.decode(arg,Base64.NO_WRAP),Charsets.UTF_8))
            val task = FutureTask {
                val context=requireNotNull(context)
                when(method) {
                    "bubbleConfig" -> BubbleSettings.snapshot(context)
                    "setBubbleConfig" -> { BubbleSettings.write(context,data);RemoteInputService.active?.recheckBubbles();BubbleSettings.snapshot(context) }
                    else -> RemoteInputService.active?.execute(method,data)
                        ?: if(method=="status") JSONObject().put("ok",true).put("available",false)
                        else throw RemoteRequestException("Enable TV touchpad and bubbles in phone Settings first.")
                }
            }
            if(Looper.myLooper()==Looper.getMainLooper()) task.run() else Handler(Looper.getMainLooper()).post(task)
            try { task.get(3,TimeUnit.SECONDS) } finally { if(!task.isDone) task.cancel(false) }
        }.getOrElse {
            val cause=if(it is java.util.concurrent.ExecutionException) it.cause else it
            JSONObject().put("ok",false).put("error",if(cause is RemoteRequestException) cause.message else "TV remote request failed; check companion version, settings and accessibility access.")
        }
        return Bundle().apply { putString("PLUS_REMOTE",Base64.encodeToString(result.toString().toByteArray(),Base64.NO_WRAP)) }
    }
    override fun getType(uri:Uri):String { authorize(); return "application/json" }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor? { authorize();return null }
    override fun insert(uri:Uri,values:ContentValues?):Uri? { authorize();throw UnsupportedOperationException() }
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int { authorize();throw UnsupportedOperationException() }
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int { authorize();throw UnsupportedOperationException() }
}
