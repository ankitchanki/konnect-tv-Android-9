package io.konnecttv.glowlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.roundToInt

/** No window contents are retrieved. Foreground identity comes only from package/window-state events. */
class RemoteInputService : AccessibilityService() {
    companion object { @Volatile var active: RemoteInputService? = null; private set }
    private val handler = Handler(Looper.getMainLooper())
    private val windows get() = getSystemService(WindowManager::class.java)
    private var cursor: TextView? = null
    private var dim: View? = null
    private var bubble: TextView? = null
    private var foreground: String? = null
    private var asleep = false
    private var dreaming = false
    private var receiverRegistered = false
    private var x = 0f
    private var y = 0f
    private var brightness = 100
    private data class Message(val id: Long, val text: String)
    private data class Delivery(val id: Long, val state: String, val reason: String = "")
    private var nextId = System.currentTimeMillis()
    private val queue = ArrayDeque<Message>()
    private val deliveries = LinkedHashMap<Long, Delivery>()
    private var current: Message? = null
    private val hideCursor = Runnable { remove(cursor); cursor = null }
    private val fadeBubble = Runnable {
        bubble?.animate()?.alpha(0f)?.setDuration(250)?.withEndAction {
            val finished = current
            remove(bubble);bubble=null;current=null
            finished?.let { record(it.id,"finished") }
            showNext()
        }?.start()
    }
    private val screenState = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { asleep=true;clearDimming();clearBubbles("Not shown: TV screen is off.") }
                Intent.ACTION_SCREEN_ON -> asleep=false
                Intent.ACTION_DREAMING_STARTED -> { dreaming=true;clearDimming();clearBubbles("Not shown: TV screensaver is active.") }
                Intent.ACTION_DREAMING_STOPPED -> dreaming=false
            }
        }
    }
    override fun onServiceConnected() {
        clear()
        active=this
        asleep=!getSystemService(PowerManager::class.java).isInteractive
        foreground=null;dreaming=false
        x=resources.displayMetrics.widthPixels/2f;y=resources.displayMetrics.heightPixels/2f
        if(!receiverRegistered) {
            val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_DREAMING_STARTED);addAction(Intent.ACTION_DREAMING_STOPPED) }
            if(Build.VERSION.SDK_INT>=33) registerReceiver(screenState,filter,Context.RECEIVER_EXPORTED) else registerReceiver(screenState,filter)
            receiverRegistered=true
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg=event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // Our own non-focusable cursor/bubble must not masquerade as the TV Home screen.
        if(pkg==packageName && event.className?.toString()!=HomeActivity::class.java.name) return
        foreground=pkg
        recheckBubbles()
    }
    override fun onInterrupt() { clear() }
    override fun onDestroy() {
        active=null;clear()
        if(receiverRegistered) { unregisterReceiver(screenState);receiverRegistered=false }
        super.onDestroy()
    }
    val isDimmed get() = dim!=null
    fun clearDimming() { remove(dim);dim=null;brightness=100 }
    fun resetOverlays() = clear()
    private fun clear() {
        handler.removeCallbacksAndMessages(null)
        remove(cursor);cursor=null;clearDimming()
        clearBubbles(if(getSharedPreferences("remote_privacy",0).getBoolean("overlays",true)) "Not shown: TV overlay service was reset." else "Not shown: TV owner blocked overlays in Glowlauncher privacy settings.")
    }
    private fun clearBubbles(reason: String) {
        handler.removeCallbacks(fadeBubble)
        bubble?.animate()?.cancel();remove(bubble);bubble=null
        current?.let { record(it.id,"blocked",reason) };current=null
        while(queue.isNotEmpty()) record(queue.removeFirst().id,"blocked",reason)
    }
    private fun remove(view: View?) { view?.let { runCatching { windows.removeView(it) } } }
    private fun dp(value: Int) = (value*resources.displayMetrics.density).roundToInt()
    private fun params(width: Int, height: Int, gravity: Int) = WindowManager.LayoutParams(width,height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply { this.gravity=gravity }
    private fun label(pkg: String?): String? = pkg?.let { runCatching { packageManager.getApplicationInfo(it,0).loadLabel(packageManager).toString() }.getOrDefault(it) }
    private fun blockedReason(): String? {
        if(asleep) return "Not shown: TV screen is off."
        if(dreaming) return "Not shown: TV screensaver is active."
        return BubbleSettings.read(this).blockedReason(foreground,label(foreground),getSharedPreferences("remote_privacy",0).getBoolean("overlays",true))
    }
    private fun record(id: Long, state: String, reason: String = "") {
        deliveries[id]=Delivery(id,state,reason)
        while(deliveries.size>32) deliveries.remove(deliveries.keys.first())
    }
    fun recheckBubbles() {
        val reason=blockedReason()
        if(reason!=null) clearBubbles(reason) else if(bubble==null) showNext()
    }
    private fun showNext() {
        if(bubble!=null || queue.isEmpty()) return
        val reason=blockedReason()
        if(reason!=null) { clearBubbles(reason);return }
        val message=queue.removeFirst();current=message
        bubble=TextView(this).apply {
            text="Konnect TV Plus\n${message.text}";textSize=18f;setTextColor(Color.WHITE)
            setPadding(dp(20),dp(14),dp(20),dp(14));alpha=0f
            background=GradientDrawable().apply { setColor(Color.rgb(28,32,40));cornerRadius=dp(22).toFloat();setStroke(dp(1).coerceAtLeast(1),Color.rgb(123,239,184)) }
            elevation=dp(8).toFloat();maxLines=6;ellipsize=TextUtils.TruncateAt.END
        }
        windows.addView(bubble,params(minOf(dp(480),resources.displayMetrics.widthPixels-dp(64)),-2,Gravity.TOP or Gravity.RIGHT).apply { x=dp(24);y=dp(24) })
        record(message.id,"shown")
        bubble!!.animate().alpha(1f).setDuration(250).start()
        handler.postDelayed(fadeBubble,6750)
    }
    private fun snapshot() = JSONObject().put("ok",true).put("available",true).put("brightness",brightness)
        .put("cursorVisible",cursor!=null).put("bubbleVisible",bubble!=null).put("currentMessageId",current?.id ?: 0)
        .put("queuedMessages",queue.size).put("foregroundPackage",foreground.orEmpty())
        .put("messages",JSONArray(deliveries.values.map { JSONObject().put("id",it.id).put("state",it.state).put("reason",it.reason) }))
    fun execute(method: String, data: JSONObject): JSONObject {
        if(!getSharedPreferences("remote_privacy",0).getBoolean("overlays",true)) {
            clear()
            if(method=="message") throw RemoteRequestException(blockedReason()!!)
            if(method!="status") throw RemoteRequestException("TV owner blocked overlays in Glowlauncher privacy settings.")
            return snapshot().put("available",false)
        }
        var messageId: Long?=null
        when(method) {
            "status" -> Unit
            "move" -> {
                val width=resources.displayMetrics.widthPixels;val height=resources.displayMetrics.heightPixels
                x=(x+data.optInt("dx").coerceIn(-250,250)).coerceIn(0f,(width-dp(28)).coerceAtLeast(0).toFloat())
                y=(y+data.optInt("dy").coerceIn(-250,250)).coerceIn(0f,(height-dp(28)).coerceAtLeast(0).toFloat())
                if(cursor==null) { cursor=TextView(this).apply { text="●";textSize=22f;setTextColor(Color.rgb(123,239,184));setShadowLayer(dp(2).toFloat(),0f,0f,Color.BLACK) };windows.addView(cursor,params(dp(28),dp(28),Gravity.TOP or Gravity.LEFT)) }
                val p=cursor!!.layoutParams as WindowManager.LayoutParams;p.x=x.toInt();p.y=y.toInt();windows.updateViewLayout(cursor,p)
                handler.removeCallbacks(hideCursor);handler.postDelayed(hideCursor,8000)
            }
            "click" -> {
                val path=Path().apply { moveTo(x+dp(14),y+dp(14)) }
                if(!dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,70)).build(),null,null)) throw RemoteRequestException("TV did not accept pointer tap.")
            }
            "dim" -> {
                if((asleep || dreaming) && data.optInt("value")!=100) { clearDimming();throw RemoteRequestException("Screen dimming is unavailable while TV is asleep or showing a screensaver.") }
                brightness=data.getInt("value").coerceIn(20,100)
                if(brightness==100) clearDimming()
                else { if(dim==null) { dim=View(this).apply { setBackgroundColor(Color.BLACK) };windows.addView(dim,params(-1,-1,Gravity.FILL)) };dim!!.alpha=(100-brightness)/100f*.8f }
            }
            "message" -> {
                val text=data.getString("text").trim()
                if(text.isBlank() || text.length>240) throw RemoteRequestException("Use 1–240 characters.")
                blockedReason()?.let { throw RemoteRequestException(it) }
                if(queue.size>=10) throw RemoteRequestException("TV message queue is full. Try again shortly.")
                messageId=++nextId
                queue.addLast(Message(messageId,text));record(messageId,"queued");showNext()
            }
            "hide" -> { handler.removeCallbacks(hideCursor);remove(cursor);cursor=null }
            else -> error("Unknown remote action")
        }
        return snapshot().also { result -> messageId?.let { result.put("messageId",it).put("delivery",deliveries[it]?.state) } }
    }
}
