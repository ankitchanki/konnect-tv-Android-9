package io.konnecttv.glowlauncher

import android.content.Context
import android.content.Intent
import io.konnecttv.plus.BubbleConfig
import io.konnecttv.plus.BubbleScope
import io.konnecttv.plus.TV_HOME_PACKAGE
import org.json.JSONArray
import org.json.JSONObject

/** Configuration belongs to the TV, shared by every authorized phone. No message text is stored. */
internal object BubbleSettings {
    fun read(context: Context): BubbleConfig {
        val prefs=context.getSharedPreferences("bubble_settings",0)
        return BubbleConfig(prefs.getBoolean("enabled",true),runCatching { BubbleScope.valueOf(prefs.getString("scope","ANY")!!) }.getOrDefault(BubbleScope.ANY),prefs.getStringSet("packages",emptySet()).orEmpty().toSet())
    }
    fun write(context: Context, data: JSONObject) {
        require(data.get("enabled") is Boolean)
        val scope=BubbleScope.valueOf(data.getString("scope"))
        val list=data.getJSONArray("packages");require(list.length()<=100)
        val packages=(0 until list.length()).map { list.getString(it) }.toSet()
        require(packages.all { it.length in 1..255 && it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) })
        check(context.getSharedPreferences("bubble_settings",0).edit().putBoolean("enabled",data.getBoolean("enabled")).putString("scope",scope.name).putStringSet("packages",packages).commit())
    }
    fun snapshot(context: Context): JSONObject {
        val config=read(context)
        val pm=context.packageManager
        val apps=listOf(Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER).flatMap { category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category),0)
        }.map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.enabled && it.packageName != TV_HOME_PACKAGE }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        return JSONObject().put("ok",true).put("config",JSONObject().put("enabled",config.enabled).put("scope",config.scope.name).put("packages",JSONArray(config.packages.sorted())))
            .put("apps",JSONArray(apps.map { JSONObject().put("package",it.packageName).put("label",it.loadLabel(pm).toString().take(160)) }))
    }
}
internal class RemoteRequestException(message: String): IllegalStateException(message)
