package io.konnecttv.glowlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import org.json.JSONObject

/** Explicitly enabled input/overlay service. Does not retrieve window contents or intercept Home. */
class RemoteInputService : AccessibilityService() {
    companion object { @Volatile var active: RemoteInputService? = null; private set }
    private val handler = Handler(Looper.getMainLooper())
    private val windows get() = getSystemService(WindowManager::class.java)
    private var cursor: TextView? = null
    private var dim: View? = null
    private var bubble: TextView? = null
    private var x = 0f
    private var y = 0f
    private var brightness = 100
    private val hideCursor = Runnable { remove(cursor); cursor = null }
    private val hideBubble = Runnable { remove(bubble); bubble = null }
    override fun onServiceConnected() { active = this; x = resources.displayMetrics.widthPixels / 2f; y = resources.displayMetrics.heightPixels / 2f }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { clear() }
    override fun onDestroy() { active = null; clear(); super.onDestroy() }
    fun resetOverlays() = clear()
    private fun clear() { handler.removeCallbacksAndMessages(null); remove(cursor); remove(dim); remove(bubble); cursor = null; dim = null; bubble = null; brightness = 100 }
    private fun remove(view: View?) { view?.let { runCatching { windows.removeView(it) } } }
    private fun params(width: Int, height: Int, gravity: Int) = WindowManager.LayoutParams(width,height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply { this.gravity = gravity }
    fun execute(method: String, data: JSONObject): JSONObject {
        if(!getSharedPreferences("remote_privacy",0).getBoolean("overlays",true)) {
            clear()
            check(method=="status") { "Overlays blocked in TV phone privacy settings." }
            return JSONObject().put("ok",true).put("available",false).put("brightness",100)
        }
        when(method) {
            "status" -> Unit
            "move" -> {
                val width = resources.displayMetrics.widthPixels; val height = resources.displayMetrics.heightPixels
                x = (x + data.optInt("dx").coerceIn(-250,250)).coerceIn(2f,(width-32).toFloat())
                y = (y + data.optInt("dy").coerceIn(-250,250)).coerceIn(2f,(height-32).toFloat())
                if (cursor == null) {
                    cursor = TextView(this).apply { text = "●"; textSize = 22f; setTextColor(Color.rgb(123,239,184)); setShadowLayer(3f,0f,0f,Color.BLACK) }
                    windows.addView(cursor,params(40,40,Gravity.TOP or Gravity.LEFT))
                }
                val p = cursor!!.layoutParams as WindowManager.LayoutParams; p.x = x.toInt(); p.y = y.toInt(); windows.updateViewLayout(cursor,p)
                handler.removeCallbacks(hideCursor); handler.postDelayed(hideCursor,8000)
            }
            "click" -> {
                val path = Path().apply { moveTo(x+10,y+10) }
                check(dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,70)).build(),null,null)) { "TV did not accept pointer tap." }
            }
            "dim" -> {
                brightness = data.getInt("value").coerceIn(20,100)
                if (brightness == 100) { remove(dim); dim = null }
                else {
                    if (dim == null) { dim = View(this).apply { setBackgroundColor(Color.BLACK) }; windows.addView(dim,params(-1,-1,Gravity.FILL)) }
                    dim!!.alpha = (100-brightness)/100f * .8f
                }
            }
            "message" -> {
                val text = data.getString("text").trim(); require(text.isNotBlank() && text.length <= 240) { "Use 1–240 characters." }
                remove(bubble)
                bubble = TextView(this).apply {
                    this.text = "Konnect TV Plus\n$text"; textSize = 18f; setTextColor(Color.WHITE); setPadding(28,18,28,18)
                    background = GradientDrawable().apply { setColor(Color.rgb(28,32,40)); cornerRadius = 28f; setStroke(2,Color.rgb(123,239,184)) }
                    elevation = 12f; maxLines = 5
                }
                windows.addView(bubble,params((resources.displayMetrics.widthPixels * .45f).toInt(),-2,Gravity.TOP or Gravity.RIGHT).apply { x=32; y=32 })
                handler.removeCallbacks(hideBubble); handler.postDelayed(hideBubble,6500)
            }
            "hide" -> { handler.removeCallbacks(hideCursor); remove(cursor); cursor = null }
            else -> error("Unknown remote action")
        }
        return JSONObject().put("ok",true).put("available",true).put("brightness",brightness).put("cursorVisible",cursor!=null).put("bubbleVisible",bubble!=null)
    }
}
