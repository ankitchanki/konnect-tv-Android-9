package io.konnecttv.data

import android.content.Context
import org.json.JSONObject

data class TvApp(val pkg: String, val label: String, val system: Boolean, val disabled: Boolean)

/** A package the list says is safe to turn off. [optional] ones start unticked. */
data class SafeEntry(val pkg: String, val label: String, val reason: String, val optional: Boolean)

enum class Group { SAFE, NEEDED, YOURS }

/**
 * The debloat rules, shipped as assets/debloat.json so the list can grow without code changes.
 */
class DebloatList(
    val safe: Map<String, SafeEntry>,
    private val protectedPkgs: Set<String>,
    private val protectedParts: List<String>,
    private val labels: Map<String, String>,
) {
    /** Never turned off, from Detox or the app manager: Play Store, keyboards, Bluetooth, HDMI inputs. */
    fun isProtected(pkg: String) = pkg in protectedPkgs || protectedParts.any { pkg.contains(it) }

    fun group(app: TvApp): Group = when {
        !app.system -> Group.YOURS
        // ponytail: system apps not on the list stay locked here; the app manager can still disable them.
        app.pkg in safe && !isProtected(app.pkg) -> Group.SAFE
        else -> Group.NEEDED
    }

    fun label(pkg: String) = safe[pkg]?.label ?: labels[pkg] ?: prettyName(pkg)

    companion object {
        fun load(context: Context): DebloatList {
            val json = JSONObject(context.assets.open("debloat.json").bufferedReader().readText())
            val safe = json.getJSONArray("safe").let { a ->
                List(a.length()) { a.getJSONObject(it) }.associate {
                    it.getString("pkg") to SafeEntry(it.getString("pkg"), it.getString("label"), it.getString("reason"), it.optBoolean("optional"))
                }
            }
            fun strings(key: String) = json.getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }
            val labels = json.getJSONObject("labels").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            return DebloatList(safe, strings("protected").toSet(), strings("protectedParts"), labels)
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
