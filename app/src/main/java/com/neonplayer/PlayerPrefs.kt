package com.neonplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlayerSettings(
    val rememberPosition: Boolean = true,
    val autoplay: Boolean = false,
    val doubleTapSeekSeconds: Int = 10,
    val longPressSpeed: Float = 2f,
    val verticalGestures: Boolean = true,
    val swipeSeek: Boolean = true
)

data class Playlist(val id: String, val name: String, val videoIds: List<Long>)

class PlayerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("neon_player_v2", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> = prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    fun saveFavorites(ids: Set<Long>) { prefs.edit().putStringSet("favorites", ids.map(Long::toString).toSet()).apply() }

    fun progress(): Map<Long, Long> {
        val json = runCatching { JSONObject(prefs.getString("progress", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return buildMap { json.keys().forEach { key -> put(key.toLongOrNull() ?: return@forEach, json.optLong(key)) } }
    }
    fun saveProgress(values: Map<Long, Long>) {
        val json = JSONObject(); values.forEach { (id, position) -> json.put(id.toString(), position) }
        prefs.edit().putString("progress", json.toString()).apply()
    }

    fun playlists(): List<Playlist> {
        val array = runCatching { JSONArray(prefs.getString("playlists", "[]") ?: "[]") }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val ids = obj.optJSONArray("videos") ?: JSONArray()
                add(Playlist(obj.optString("id"), obj.optString("name"), buildList { for (j in 0 until ids.length()) add(ids.optLong(j)) }))
            }
        }
    }
    fun savePlaylists(items: List<Playlist>) {
        val array = JSONArray()
        items.forEach { p ->
            val ids = JSONArray(); p.videoIds.forEach { ids.put(it) }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("videos", ids))
        }
        prefs.edit().putString("playlists", array.toString()).apply()
    }

    fun settings(): PlayerSettings = PlayerSettings(
        rememberPosition = prefs.getBoolean("remember", true),
        autoplay = prefs.getBoolean("autoplay", false),
        doubleTapSeekSeconds = prefs.getInt("double_seek", 10),
        longPressSpeed = prefs.getFloat("long_speed", 2f),
        verticalGestures = prefs.getBoolean("vertical_gestures", true),
        swipeSeek = prefs.getBoolean("swipe_seek", true)
    )
    fun saveSettings(s: PlayerSettings) { prefs.edit().putBoolean("remember", s.rememberPosition).putBoolean("autoplay", s.autoplay).putInt("double_seek", s.doubleTapSeekSeconds).putFloat("long_speed", s.longPressSpeed).putBoolean("vertical_gestures", s.verticalGestures).putBoolean("swipe_seek", s.swipeSeek).apply() }
}
