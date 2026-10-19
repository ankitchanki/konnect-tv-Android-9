package io.konnecttv.glowlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Makes the Home button open Glowlauncher on TVs that won't let another app be the home screen
 * (Google TV ranks its own launcher above every installed one). Nothing on the TV is turned off:
 * Home is answered here, and when the TV shows its own home anyway (at boot, or after leaving an
 * app) Glowlauncher is brought straight back in front of it. KonnectTV turns this on only when needed.
 */
class HomeButtonService : AccessibilityService() {

    private var stockHome: ComponentName? = null

    override fun onServiceConnected() {
        stockHome = findStockHome()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val home = stockHome ?: return
        if (event.packageName == home.packageName && event.className == home.className) openHome()
    }

    override fun onInterrupt() {}

    private fun openHome() {
        runCatching {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
    }

    /** The home screen the TV itself would open; only that exact screen is covered, never the rest of its app. */
    private fun findStockHome(): ComponentName? {
        val info = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo ?: return null
        if (info.packageName == packageName || info.packageName == "android") return null
        return ComponentName(info.packageName, info.name)
    }
}
