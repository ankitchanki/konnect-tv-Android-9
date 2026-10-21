package io.konnecttv.plus

import io.konnecttv.plus.data.SavedTv
import org.json.JSONArray
import org.json.JSONObject

/** Only connection hints and optional UI preferences. No ADB key/certificate, access grants or undo history. */
data class SetupTransfer(val tvs: List<SavedTv>, val haptics: Boolean, val oneHanded: Boolean, val touchpad: Boolean) {
    fun json(): String = JSONObject().put("format","konnect-plus-setup").put("version",1)
        .put("tvs",JSONArray(tvs.map { JSONObject().put("id",it.id).put("name",it.name).put("host",it.host).put("port",it.port).put("tag",it.tag) }))
        .put("haptics",haptics).put("oneHanded",oneHanded).put("touchpad",touchpad).toString(2)
    companion object {
        fun parse(text: String): SetupTransfer {
            require(text.toByteArray().size <= 32768) { "Setup file is too large." }
            val json = JSONObject(text)
            require(json.getString("format")=="konnect-plus-setup" && json.getInt("version")==1) { "Unsupported setup file." }
            val array = json.getJSONArray("tvs");require(array.length()<=30) { "At most 30 saved TVs can be imported." }
            val tvs = (0 until array.length()).map { i ->
                val item=array.getJSONObject(i)
                val id=item.getString("id");val name=item.getString("name");val host=item.getString("host");val tag=item.optString("tag");val port=item.getInt("port")
                require(id.length in 1..160 && name.length in 1..80 && host.length in 1..253 && host.matches(Regex("[A-Za-z0-9.:%_-]+")) && tag.length<=80 && port in 1..65535) { "Invalid TV entry." }
                require(listOf(id,name,host,tag).none { value -> value.any { it.code<32 } }) { "Invalid characters." }
                SavedTv(id,name,host,port,tag=tag)
            }
            require(tvs.distinctBy { it.id }.size==tvs.size) { "Duplicate TV entries." }
            return SetupTransfer(tvs,json.optBoolean("haptics",true),json.optBoolean("oneHanded",false),json.optBoolean("touchpad",false))
        }
    }
}
