package io.konnecttv.glowlauncher

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView

/**
 * Glowlauncher: a calm TV home. A big clock, the apps you picked in one glowing dock, and Inputs,
 * Settings and network at the top right. Nothing loads from the internet and nothing animates
 * while idle, so it stays light on 1 GB TVs.
 *
 * KonnectTV sets the apps and glow colour with:
 *   am start -n io.konnecttv.glowlauncher/.HomeActivity --es apps "a,b" --es accent 3FD6A0 --es stamp <n>
 * (--es accent rgb  slowly fades the glow through the colours.)
 * On the TV, hold OK (or press Menu) on an app to move or remove it; the last tile adds one.
 */
class HomeActivity : Activity() {

    private companion object {
        const val PREFS = "config"
        const val ADD = "add"
        const val INPUTS = "inputs"
        const val SETTINGS = "settings"
        const val NETWORK = "network"
        // Designed at 1920x1080, scaled to the TV's real UI size by px().
        const val MARGIN = 96
        const val TILE = 128
        const val GAP = 44
        const val DOCK_PAD = 34
        const val RADIUS = 32
        const val HALO = 440
        const val MAX_AUTO = 8
        const val MINT = 0x3FD6A0
        // Never on the dock by themselves: the TV's own launchers and settings.
        val NOT_APPS = setOf(
            "com.android.vending", "com.android.tv.settings", "com.android.tv", "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher",
        )
        // Shown first when KonnectTV hasn't sent a list yet, if installed.
        val POPULAR = listOf(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv",
            "in.startv.hotstar", "com.disney.disneyplus", "com.spotify.tv.android", "com.plexapp.android",
        )
    }

    private var scale = 1f
    private val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var dockScroll: HorizontalScrollView
    private lateinit var halo: View
    private lateinit var caption: TextView
    private lateinit var hint: TextView
    private lateinit var inputsIcon: View
    private lateinit var netIcon: ImageView

    private val apps = mutableListOf<String>()
    private var dirty = true
    private var focusedKey: String? = null
    private var movingKey: String? = null
    private var longPressed = false

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { dirty = true; if (hasWindowFocus()) rebuild() }
    }
    private val networkChanged = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) { runOnUiThread(::refreshNetwork) }
        override fun onLost(n: Network) { runOnUiThread(::refreshNetwork) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        saveConfig(intent)
        scale = minOf(resources.displayMetrics.widthPixels / 1920f, resources.displayMetrics.heightPixels / 1080f)

        root = FrameLayout(this).apply { clipChildren = false }

        halo = View(this).apply {
            alpha = 0f
        }
        root.addView(halo, FrameLayout.LayoutParams(px(HALO), px(HALO)))

        val time = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(220).toFloat())
            setTextColor(Color.WHITE)
            typeface = light
            letterSpacing = -0.04f
            includeFontPadding = false
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(time, FrameLayout.LayoutParams(-1, -2).apply { topMargin = px(210) })
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32).toFloat())
            setTextColor(0x99FFFFFF.toInt())
            typeface = regular
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(date, FrameLayout.LayoutParams(-1, -2).apply { topMargin = px(470) })

        // Top right: Inputs, Settings, network. Added after the dock so an app gets first focus.
        val sys = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        dock = LinearLayout(this).apply {
            setPadding(px(DOCK_PAD), px(DOCK_PAD), px(DOCK_PAD), px(DOCK_PAD))
            clipToPadding = false
            clipChildren = false
        }
        dockScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            isSmoothScrollingEnabled = true
            addView(dock)
            viewTreeObserver.addOnScrollChangedListener { currentFocus?.takeIf { it.parent == dock }?.let(::moveHalo) }
        }
        val pill = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = px(TILE).toFloat()
                setColor(0x0DFFFFFF)
                setStroke(px(2).coerceAtLeast(1), 0x17FFFFFF)
            }
            clipChildren = false
            addView(dockScroll)
        }
        root.addView(pill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin = px(MARGIN); rightMargin = px(MARGIN); bottomMargin = px(150)
        })

        caption = text(px(30), Color.WHITE, medium).apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = px(84) })
        hint = text(px(26), 0xB3FFFFFF.toInt(), regular).apply {
            text = "Use ◀ ▶ to move it, then press OK"
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = px(370) })

        inputsIcon = sysIcon(INPUTS, "Inputs", Glyph.INPUT)
        sys.addView(inputsIcon)
        sys.addView(sysIcon(SETTINGS, "Settings", Glyph.SETTINGS))
        netIcon = ImageView(this)
        sys.addView(sysIcon(NETWORK, "Network", null, netIcon))
        root.addView(sys, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = px(56); rightMargin = px(MARGIN - 18)
        })

        setContentView(root)

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(packagesChanged, f)
        runCatching { connectivity().registerNetworkCallback(NetworkRequest.Builder().build(), networkChanged) }
    }

    override fun onDestroy() {
        unregisterReceiver(packagesChanged)
        runCatching { connectivity().unregisterNetworkCallback(networkChanged) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (saveConfig(intent)) rebuild()
        // Home pressed while already home: back to the first app, like every TV launcher.
        else if (movingKey == null) dock.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (dirty) rebuild()
        startRainbow()
        refreshNetwork()
        if (currentFocus == null) dock.getChildAt(0)?.requestFocus()
    }

    // The RGB fade only runs while home is on screen, so it never costs an app anything.
    override fun onPause() {
        stopRainbow()
        super.onPause()
    }

    @Deprecated("Home has nothing to go back to.")
    override fun onBackPressed() {}

    /** Saves apps and accent sent with --es. The stamp stops Android's restart of an old intent undoing edits made on the TV. */
    private fun saveConfig(intent: Intent?): Boolean {
        val list = intent?.getStringExtra("apps")
        val accentText = intent?.getStringExtra("accent")?.trim()
        val rainbow = accentText.equals("rgb", ignoreCase = true)
        val accent = accentText?.removePrefix("#")?.toIntOrNull(16)
        if (list == null && accent == null && !rainbow) return false
        val prefs = getSharedPreferences(PREFS, 0)
        val stamp = intent?.getStringExtra("stamp")
        if (stamp != null && stamp == prefs.getString("stamp", null)) return false
        prefs.edit().apply {
            if (stamp != null) putString("stamp", stamp)
            if (list != null) {
                if (list.isBlank() || list.trim().equals("auto", true)) remove("apps") else putString("apps", list.trim())
            }
            if (accent != null) putInt("accent", accent and 0xFFFFFF).putBoolean("rgb", false)
            if (rainbow) putBoolean("rgb", true)
        }.apply()
        dirty = true
        return true
    }

    private fun appList(): List<String> {
        val saved = getSharedPreferences(PREFS, 0).getString("apps", null)
        val out = (saved?.split(Regex("[,\\s]+")) ?: POPULAR)
            .filter { it.isNotEmpty() && launchIntent(it) != null }.distinct().toMutableList()
        if (saved == null) {
            // Top up with TV apps people installed themselves.
            for (ri in packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
                if (out.size >= MAX_AUTO) break
                val p = ri.activityInfo.packageName
                val flags = ri.activityInfo.applicationInfo.flags
                val stock = flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                if (!stock && p !in out && p !in NOT_APPS && p != packageName) out += p
            }
        }
        return out
    }

    private fun rebuild() {
        dirty = false
        val want = focusedKey
        // Hold focus off while tiles are swapped, or removing the focused one hands focus along and overwrites focusedKey.
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        dock.removeAllViews()
        rings.clear()
        applyAccent()
        movingKey = null
        hint.visibility = View.GONE
        apps.clear()
        apps += appList()
        for (pkg in apps) dock.addView(tile(pkg, labelOf(pkg), appIcon(pkg), null))
        dock.addView(tile(ADD, "Add app", null, Glyph.PLUS))
        inputsIcon.visibility = if (hdmiInputs().isEmpty()) View.GONE else View.VISIBLE
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (dock.findViewWithTag<View>(want) ?: dock.getChildAt(0))?.requestFocus()
    }

    private fun tile(key: String, label: String, icon: Drawable?, glyph: Glyph?): View {
        val size = px(TILE)
        val card = FrameLayout(this)
        card.background = GradientDrawable().apply {
            cornerRadius = px(RADIUS).toFloat()
            setColor(if (glyph != null) 0x14FFFFFF else 0xFF16191E.toInt())
            setStroke(px(2).coerceAtLeast(1), 0x14FFFFFF)
        }
        card.clipToOutline = true
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        if (glyph != null) {
            iv.setImageDrawable(glyph)
            card.addView(iv, FrameLayout.LayoutParams(px(52), px(52), Gravity.CENTER))
        } else {
            iv.setImageDrawable(icon)
            card.addView(iv, FrameLayout.LayoutParams(-1, -1))
        }
        card.isFocusable = true
        card.tag = key
        card.contentDescription = label
        val ring = GradientDrawable().apply { cornerRadius = px(RADIUS).toFloat(); setStroke(px(4), lighter(accent)) }
        rings += ring
        card.setOnFocusChangeListener { v, has ->
            if (has) {
                focusedKey = key
                caption.text = label
                v.post { moveHalo(v) }
            }
            if (key != movingKey) card.foreground = if (has) ring else null
            v.animate().scaleX(if (has) 1.14f else 1f).scaleY(if (has) 1.14f else 1f).setDuration(150).start()
        }
        card.setOnClickListener { open(key) }
        val editable = key != ADD
        card.setOnKeyListener { _, code, ev -> onTileKey(key, editable, code, ev) }
        card.layoutParams = LinearLayout.LayoutParams(size, size).apply { if (dock.childCount > 0) leftMargin = px(GAP) }
        return card
    }

    private fun onTileKey(key: String, editable: Boolean, code: Int, ev: KeyEvent): Boolean {
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val down = ev.action == KeyEvent.ACTION_DOWN
        if (key == movingKey) return moveKey(key, code, ok, down)
        if (editable && code == KeyEvent.KEYCODE_MENU) {
            if (!down) showEditMenu(key)
            return true
        }
        if (!ok) return false
        if (down) {
            if (ev.repeatCount == 0) longPressed = false
            else if (editable && !longPressed &&
                (ev.isLongPress || ev.eventTime - ev.downTime >= ViewConfiguration.getLongPressTimeout())) {
                longPressed = true
                showEditMenu(key)
            }
        } else if (!longPressed) open(key)
        return true
    }

    // Move mode: an outline on the tile, ◀ ▶ slide it along the dock, OK or Back finishes.
    private fun startMove(key: String) {
        val card = dock.findViewWithTag<FrameLayout>(key) ?: return
        movingKey = key
        card.foreground = GradientDrawable().apply { cornerRadius = px(RADIUS).toFloat(); setStroke(px(5), Color.WHITE) }
        hint.visibility = View.VISIBLE
        card.requestFocus()
    }

    private fun moveKey(key: String, code: Int, ok: Boolean, down: Boolean): Boolean {
        val step = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
        if (step != 0) {
            val from = apps.indexOf(key)
            val to = from + step
            if (down && to in apps.indices) {
                apps.add(to, apps.removeAt(from))
                val card = dock.getChildAt(from)
                dock.removeViewAt(from)
                dock.addView(card, to)
                for (i in 0 until dock.childCount) (dock.getChildAt(i).layoutParams as LinearLayout.LayoutParams).leftMargin = if (i == 0) 0 else px(GAP)
                dock.requestLayout()
                card.requestFocus()
                card.post { card.requestRectangleOnScreen(android.graphics.Rect(0, 0, card.width, card.height)); moveHalo(card) }
            }
            return true
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null
                longPressed = false
                (dock.findViewWithTag<FrameLayout>(key))?.foreground = null
                dock.findViewWithTag<View>(key)?.let { it.onFocusChangeListener?.onFocusChange(it, it.hasFocus()) }
                hint.visibility = View.GONE
                saveApps()
            }
            return true
        }
        return true // up and down stay put while moving
    }

    private fun showEditMenu(key: String) {
        val user = runCatching { packageManager.getApplicationInfo(key, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0 }.getOrDefault(false)
        val items = listOf("Move", "Remove from home", if (user) "Uninstall" else "App info")
        panel(labelOf(key), items, null) { which ->
            when (which) {
                0 -> startMove(key)
                1 -> {
                    val index = apps.indexOf(key)
                    apps.remove(key)
                    saveApps()
                    focusedKey = apps.getOrNull(index.coerceAtMost(apps.size - 1)) ?: ADD
                    rebuild()
                }
                else -> runCatching {
                    val uri = Uri.parse("package:$key")
                    startActivity(Intent(if (user) Intent.ACTION_DELETE else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                }
            }
        }
    }

    private fun showAddMenu() {
        val byLabel = sortedMapOf<String, String>(String.CASE_INSENSITIVE_ORDER)
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val p = ri.activityInfo.packageName
                if (p in apps || p == packageName || p in byLabel.values || (p in NOT_APPS && p != "com.android.vending")) continue
                byLabel.putIfAbsent(labelOf(p), p)
            }
        }
        if (byLabel.isEmpty()) return panel("Every app is already here", listOf("OK"), null) {}
        val pkgs = byLabel.values.toList()
        panel("Add an app", byLabel.keys.toList(), pkgs) { which ->
            apps += pkgs[which]
            saveApps()
            focusedKey = pkgs[which]
            rebuild()
        }
    }

    private fun saveApps() = getSharedPreferences(PREFS, 0).edit().putString("apps", apps.joinToString(",")).apply()

    private fun open(key: String) {
        when (key) {
            ADD -> showAddMenu()
            INPUTS -> showInputs()
            SETTINGS -> start(Intent(Settings.ACTION_SETTINGS))
            NETWORK -> start(Intent(Settings.ACTION_WIFI_SETTINGS)) || start(Intent(Settings.ACTION_SETTINGS))
            else -> launchIntent(key)?.let(::start)
        }
    }

    private fun start(i: Intent): Boolean = runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    private fun showInputs() {
        val inputs = hdmiInputs()
        panel("Inputs", inputs.map(::inputLabel), null) { which -> openInput(inputs[which].id) }
    }

    // The stock Live TV app if this TV has it, else whatever app the TV picks for that input.
    private fun openInput(id: String) {
        val view = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
        val stock = Intent(view).setClassName("com.android.tv", "com.android.tv.MainActivity")
        if (stock.resolveActivity(packageManager) != null && start(stock)) return
        start(view)
    }

    private fun hdmiInputs(): List<TvInputInfo> {
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return runCatching {
            tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(this) }.sortedBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun inputLabel(info: TvInputInfo): String {
        if (Build.VERSION.SDK_INT >= 24) info.loadCustomLabel(this)?.takeIf { it.isNotEmpty() }?.let { return it.toString() }
        return info.loadLabel(this)?.takeIf { it.isNotEmpty() }?.toString() ?: "HDMI"
    }

    /** A side panel: a title and one row per choice. Back closes it. iconPkgs puts each app's icon by its name. */
    private fun panel(title: String, labels: List<String>, iconPkgs: List<String>?, pick: (Int) -> Unit) {
        val d = Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(48), px(88), px(48), px(48))
        }
        box.addView(text(px(40), Color.WHITE, medium).apply {
            text = title
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(px(24), 0, px(24), 0)
        })
        val size = px(52)
        val icons = arrayOfNulls<Drawable>(labels.size)
        val list = ListView(this).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            selector = GradientDrawable().apply { cornerRadius = px(22).toFloat(); setColor(0x33000000 or accent) }
            adapter = object : BaseAdapter() {
                override fun getCount() = labels.size
                override fun getItem(pos: Int) = labels[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convert: View?, parent: ViewGroup?): View {
                    val t = convert as? TextView ?: text(px(30), 0xE6FFFFFF.toInt(), regular).apply {
                        setSingleLine(true)
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_VERTICAL
                        minHeight = px(88)
                        setPadding(px(24), 0, px(24), 0)
                        compoundDrawablePadding = px(24)
                    }
                    t.text = labels[pos]
                    val icon = iconPkgs?.let {
                        icons[pos] ?: (runCatching { fit(packageManager.getApplicationIcon(it[pos]), size, size) }.getOrNull()
                            ?: ColorDrawable(Color.TRANSPARENT)).also { d -> d.setBounds(0, 0, size, size); icons[pos] = d }
                    }
                    t.setCompoundDrawablesRelative(icon, null, null, null)
                    return t
                }
            }
            setOnItemClickListener { _, _, pos, _ -> d.dismiss(); pick(pos) }
        }
        box.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = px(36) })
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(0xF70E1013.toInt()))
            setLayout(px(640), -1)
            setGravity(Gravity.END)
            setDimAmount(0.5f)
        }
        d.show()
    }

    /** A plain icon at the top right; a glass circle shows behind it only while focused. */
    private fun sysIcon(key: String, label: String, glyph: Glyph?, iv: ImageView = ImageView(this)): View {
        val b = px(72)
        val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
        return FrameLayout(this).apply {
            background = bg
            if (glyph != null) iv.setImageDrawable(glyph)
            addView(iv, FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER))
            isFocusable = true
            tag = key
            contentDescription = label
            layoutParams = LinearLayout.LayoutParams(b, b).apply { leftMargin = px(14) }
            setOnFocusChangeListener { v, has ->
                bg.setColor(if (has) 0x38FFFFFF else Color.TRANSPARENT)
                if (has) { focusedKey = key; caption.text = label; halo.animate().alpha(0f).setDuration(150).start() }
                v.animate().scaleX(if (has) 1.1f else 1f).scaleY(if (has) 1.1f else 1f).setDuration(150).start()
            }
            setOnClickListener { open(key) }
        }
    }

    // The glow follows the focused app: one soft circle sliding under the dock, nothing redrawn per frame while idle.
    private fun moveHalo(v: View) {
        val at = IntArray(2)
        val origin = IntArray(2)
        v.getLocationInWindow(at)
        root.getLocationInWindow(origin)
        // getLocationInWindow includes the focus scale; undo it to find the centre.
        val cx = at[0] - origin[0] + v.width * v.scaleX / 2f
        val cy = at[1] - origin[1] + v.height * v.scaleY / 2f
        halo.animate().translationX(cx - halo.width / 2f).translationY(cy - halo.height / 2f).alpha(1f).setDuration(200).start()
    }

    private fun connectivity() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private fun refreshNetwork() {
        val info = runCatching { connectivity().activeNetworkInfo }.getOrNull()
        val glyph = when {
            info == null || !info.isConnected -> Glyph.OFFLINE
            info.type == ConnectivityManager.TYPE_WIFI -> Glyph.WIFI
            else -> Glyph.ETHERNET
        }
        netIcon.setImageDrawable(glyph)
    }

    private fun launchIntent(pkg: String): Intent? =
        packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)

    private fun labelOf(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    /** The app's icon at its sharpest density, drawn once into a tile-sized bitmap to keep memory low. */
    private fun appIcon(pkg: String): Drawable? = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        val res = packageManager.getResourcesForApplication(info)
        val d = runCatching { res.getDrawableForDensity(info.icon, DisplayMetrics.DENSITY_XXXHIGH, null) }.getOrNull()
            ?: packageManager.getApplicationIcon(info)
        val size = px(TILE)
        // Adaptive and full-bleed icons fill the tile; small legacy icons sit inside it.
        val fill = Build.VERSION.SDK_INT >= 26 && d is android.graphics.drawable.AdaptiveIconDrawable
        if (fill) fit(d, size, size) else fit(d, size, size, inset = size / 6)
    }.getOrNull()

    private fun fit(d: Drawable, w: Int, h: Int, inset: Int = 0): Drawable = try {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(inset, inset, w - inset, h - inset)
        d.draw(Canvas(b))
        BitmapDrawable(resources, b)
    } catch (t: Throwable) { d }

    /** The glow colour picked in KonnectTV (mint until one is sent), as 0xRRGGBB. */
    private var accent = MINT
    /** RGB mode: the glow fades through every hue instead of holding one colour. */
    private var rgb = false
    private val rings = mutableListOf<GradientDrawable>()
    private val glowMain = radial(0.5f, 0.62f)
    private val glowWash = radial(0.5f, 1.15f)
    private val haloGlow = GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT }
    private val hsv = floatArrayOf(0f, 0.65f, 1f)
    // One full turn of the colour wheel every 30 s, redrawn 10 times a second: each step is too small to see, and it stays light on a TV.
    // A plain timer, not an animator, so it still runs when the TV's animations are turned off.
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private var rainbowOn = false
    private val rainbowTick = object : Runnable {
        override fun run() {
            if (!rainbowOn) return
            hsv[0] = (android.os.SystemClock.uptimeMillis() % 30_000) * 360f / 30_000
            paintGlow(Color.HSVToColor(hsv) and 0xFFFFFF)
            window.decorView.invalidate()
            ticker.postDelayed(this, 100)
        }
    }

    private fun startRainbow() {
        if (!rgb || rainbowOn) return
        rainbowOn = true
        ticker.post(rainbowTick)
    }

    private fun stopRainbow() {
        rainbowOn = false
        ticker.removeCallbacks(rainbowTick)
    }

    /** Applies the saved glow: one colour, or starts the RGB fade. */
    private fun applyAccent() {
        val prefs = getSharedPreferences(PREFS, 0)
        rgb = prefs.getBoolean("rgb", false)
        val w = resources.displayMetrics.widthPixels.toFloat()
        glowMain.gradientRadius = w * 0.36f
        glowWash.gradientRadius = w * 0.5f
        haloGlow.gradientRadius = px(HALO) / 2f
        if (window.decorView.background !is LayerDrawable) {
            window.setBackgroundDrawable(LayerDrawable(arrayOf(ColorDrawable(0xFF08090B.toInt()), glowMain, glowWash)))
            halo.background = haloGlow
        }
        paintGlow(if (rgb) Color.HSVToColor(hsv) and 0xFFFFFF else prefs.getInt("accent", MINT))
        if (rgb) startRainbow() else stopRainbow()
    }

    /** Recolours the glow in place: the background, the halo under the focused app and the focus rings. */
    private fun paintGlow(color: Int) {
        accent = color
        glowMain.colors = intArrayOf(0x22000000 or color, color)
        glowWash.colors = intArrayOf(0x0E000000 or lighter(color), lighter(color) and 0xFFFFFF)
        haloGlow.colors = intArrayOf(0x66000000 or color, 0x22000000 or color, color)
        val ringColor = lighter(color)
        rings.forEach { it.setStroke(px(4), ringColor) }
    }

    private fun radial(cx: Float, cy: Float) = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        setGradientCenter(cx, cy)
        setDither(true)
    }

    /** Halfway to white: the focus ring reads as the same colour, but brighter than the glow behind it. */
    private fun lighter(rgb: Int): Int {
        fun mix(c: Int) = c + (255 - c) / 2
        return Color.rgb(mix(Color.red(rgb)), mix(Color.green(rgb)), mix(Color.blue(rgb)))
    }

    private fun text(px: Int, color: Int, tf: Typeface) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px.toFloat())
        setTextColor(color)
        typeface = tf
    }

    private fun px(v: Int) = Math.round(v * scale)
}
