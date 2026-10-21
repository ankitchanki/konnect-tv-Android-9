package io.konnecttv.plus.data

import android.content.Context
import org.json.JSONObject

data class TvApp(val pkg: String, val label: String, val system: Boolean, val disabled: Boolean, val installed: Boolean = true)

/** A package the list says is safe to turn off. [optional] ones start unticked. */
data class SafeEntry(val pkg: String, val label: String, val reason: String, val optional: Boolean, val brand: String = "Android TV", val source: String = "")

enum class Group { SAFE, NEEDED, REVIEW, YOURS }

data class TvIdentity(val manufacturer: String, val model: String, val android: String, val sdk: Int, val brand: String) {
    fun detectedBrand(packages: Collection<String>): String {
        val known = listOf("TCL", "Sony", "Xiaomi", "Hisense", "Philips", "NVIDIA", "OnePlus", "Skyworth")
        known.firstOrNull { manufacturer.contains(it, true) || brand.contains(it, true) }?.let { return it }
        return when {
            packages.any { it.startsWith("com.tcl.") } -> "TCL / TCL-built"
            packages.any { it.startsWith("com.sony.dtv.") } -> "Sony"
            packages.any { it.startsWith("com.mitv.") || it.startsWith("com.xiaomi.") } -> "Xiaomi"
            packages.any { it.startsWith("com.hisense.") } -> "Hisense"
            packages.any { it.startsWith("org.droidtv.") } -> "Philips / TP Vision"
            packages.any { it.startsWith("com.nvidia.") } -> "NVIDIA"
            else -> manufacturer.ifBlank { brand.ifBlank { "Android TV" } }
        }
    }
}

/**
 * The debloat rules, shipped as assets/debloat.json so the list can grow without code changes.
 */
class DebloatList(
    val safe: Map<String, SafeEntry>,
    private val protectedPkgs: Set<String>,
    private val protectedParts: List<String>,
    private val labels: Map<String, String>,
    private val protectedReasons: Map<String, String> = emptyMap(),
) {
    /** Never turned off, from Detox or the app manager: Play Store, keyboards, Bluetooth, HDMI inputs. */
    fun isProtected(pkg: String) = pkg in protectedPkgs || protectedParts.any { pkg.contains(it) }

    fun group(app: TvApp): Group = when {
        isProtected(app.pkg) -> Group.NEEDED
        !app.system -> Group.YOURS
        app.pkg in safe -> Group.SAFE
        else -> Group.REVIEW
    }

    fun reason(app: TvApp): String = when (group(app)) {
        Group.NEEDED -> protectedReasons[app.pkg] ?: when {
            "webview" in app.pkg -> "WebView renders sign-in, help and other app screens. Keep the TV's web renderer enabled."
            "providers" in app.pkg -> "A system data provider can be shared by Settings, media and other TV services."
            listOf("inputmethod", ".ime", "keyboard").any { it in app.pkg } -> "Needed for on-screen typing and password entry."
            "bluetooth" in app.pkg -> "Needed for Bluetooth remotes and audio devices."
            listOf("tvinput", "hdmi").any { it in app.pkg } -> "Needed for TV tuners, source selection or HDMI playback."
            else -> "A protected TV system component. Disabling it can break device functions."
        }
        Group.SAFE -> safe[app.pkg]?.reason.orEmpty()
        Group.REVIEW -> "This system package has no reviewed rule for its function. Kept enabled; its name alone does not establish that it is safe to disable."
        Group.YOURS -> safe[app.pkg]?.reason ?: "An installed app. Keep it if you use it; turning it off stops its features."
    }

    fun canDisable(app: TvApp) = app.installed && !app.disabled && !isProtected(app.pkg) && (!app.system || app.pkg in safe)

    fun label(pkg: String) = safe[pkg]?.label ?: labels[pkg] ?: prettyName(pkg)

    companion object {
        fun load(context: Context): DebloatList {
            val json = JSONObject(context.assets.open("debloat.json").bufferedReader().readText())
            val safe = json.getJSONArray("safe").let { a ->
                List(a.length()) { a.getJSONObject(it) }.associate {
                    it.getString("pkg") to SafeEntry(it.getString("pkg"), it.getString("label"), it.getString("reason"), it.optBoolean("optional"), it.optString("brand", "Android TV"), it.optString("source", ""))
                }
            }
            fun strings(key: String) = json.getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }
            val labels = json.getJSONObject("labels").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            val reasons = json.optJSONObject("protectedReasons")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
            return DebloatList(safe, strings("protected").toSet(), strings("protectedParts"), labels, reasons)
        }
    }
}

private val filler = setOf(
    "com", "org", "net", "tv", "android", "google", "apps", "app", "atv", "dtv", "livingroom", "leanback",
    "ninja", "androidtv", "mobile", "client", "release", "main", "player",
)

/**
 * A readable guess from a package name, for apps the list doesn't name:
 * "com.sony.dtv.sonyselect" -> "Sonyselect". ADB can't read real app labels without pulling each APK.
 */
fun prettyName(pkg: String): String {
    val parts = pkg.split('.').filter { it.isNotBlank() }
    val word = parts.lastOrNull { it.lowercase() !in filler } ?: parts.lastOrNull() ?: pkg
    return word.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
