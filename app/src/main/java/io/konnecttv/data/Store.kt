package io.konnecttv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedTv(
    /** ro.serialno, so a TV keeps its history when its IP changes. */
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    /** Free RAM the first time we connected, for the before/after on Boost. */
    val ramBefore: Long? = null,
)

/**
 * One undoable change.
 * kind "disable": target = package, undo = pm enable.
 * kind "setting": target = "namespace:key", old = previous value (null = was unset).
 * kind "install": target = package, undo = uninstall.
 */
data class Change(val time: Long, val kind: String, val target: String, val label: String, val old: String? = null)

/** Plain JSON files in app storage. Nothing leaves the phone. */
class Store(context: Context) {
    private val dir = context.filesDir
    private val tvsFile = File(dir, "tvs.json")

    fun tvs(): List<SavedTv> = read(tvsFile).map {
        SavedTv(
            it.getString("id"), it.getString("name"), it.getString("host"), it.getInt("port"),
            if (it.has("ramBefore")) it.getLong("ramBefore") else null,
        )
    }

    fun saveTv(tv: SavedTv) = writeTvs(listOf(tv) + tvs().filter { it.id != tv.id })

    fun forgetTv(id: String) {
        writeTvs(tvs().filter { it.id != id })
        historyFile(id).delete()
    }

    private fun writeTvs(tvs: List<SavedTv>) = write(tvsFile, tvs) {
        JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port)
            .putOpt("ramBefore", it.ramBefore)
    }

    fun history(tvId: String): List<Change> = read(historyFile(tvId)).map {
        Change(it.getLong("time"), it.getString("kind"), it.getString("target"), it.getString("label"),
            if (it.isNull("old")) null else it.getString("old"))
    }

    fun setHistory(tvId: String, changes: List<Change>) = write(historyFile(tvId), changes) {
        JSONObject().put("time", it.time).put("kind", it.kind).put("target", it.target).put("label", it.label)
            .put("old", it.old ?: JSONObject.NULL)
    }

    private fun historyFile(tvId: String) = File(dir, "history-${tvId.filter { it.isLetterOrDigit() }}.json")

    private fun read(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrElse { return emptyList() }
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun <T> write(file: File, items: List<T>, toJson: (T) -> JSONObject) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONArray(items.map(toJson)).toString())
        tmp.renameTo(file)
    }
}
