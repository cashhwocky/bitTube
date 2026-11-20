package com.bitdroid.tube.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything personal lives on the phone: subscriptions, likes, dislikes, playlists, history.
 * No Google account, nothing leaves the device.
 */
object Library {
    data class Playlist(val id: String, val name: String, val items: List<VideoRef>)

    val subscriptions = mutableStateListOf<ChannelRef>()
    val liked = mutableStateListOf<VideoRef>()
    val disliked = mutableStateListOf<String>() // video ids
    val history = mutableStateListOf<VideoRef>()
    val playlists = mutableStateListOf<Playlist>()
    val searches = mutableStateListOf<String>()
    /** Where you stopped in each video (seconds), for resume. */
    val positions = mutableStateMapOf<String, Long>()

    const val WATCH_LATER = "watch_later"

    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        runCatching {
            if (!file.exists()) return@runCatching
            val o = JSONObject(file.readText())
            o.optJSONArray("subs")?.forEachObj { subscriptions += ChannelRef.fromJson(it) }
            o.optJSONArray("liked")?.forEachObj { liked += VideoRef.fromJson(it) }
            o.optJSONArray("disliked")?.let { a -> for (i in 0 until a.length()) disliked += a.getString(i) }
            o.optJSONArray("history")?.forEachObj { history += VideoRef.fromJson(it) }
            o.optJSONArray("searches")?.let { a -> for (i in 0 until a.length()) searches += a.getString(i) }
            o.optJSONArray("playlists")?.forEachObj { p ->
                val items = mutableListOf<VideoRef>()
                p.optJSONArray("items")?.forEachObj { items += VideoRef.fromJson(it) }
                playlists += Playlist(p.getString("id"), p.optString("name"), items)
            }
            o.optJSONObject("positions")?.let { p -> p.keys().forEach { k -> positions[k] = p.getLong(k) } }
        }
        if (playlists.none { it.id == WATCH_LATER }) playlists.add(0, Playlist(WATCH_LATER, "Watch later", emptyList()))
    }

    private inline fun JSONArray.forEachObj(f: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(f)
    }

    /** Debounced write so rapid taps don't hammer the disk. */
    private fun save() {
        val snapshot = JSONObject()
            .put("subs", JSONArray(subscriptions.map { it.toJson() }))
            .put("liked", JSONArray(liked.map { it.toJson() }))
            .put("disliked", JSONArray(disliked.toList()))
            .put("history", JSONArray(history.map { it.toJson() }))
            .put("searches", JSONArray(searches.toList()))
            .put("playlists", JSONArray(playlists.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("items", JSONArray(p.items.map { it.toJson() })) }))
            .put("positions", JSONObject(positions.toMap()))
        pending?.cancel()
        pending = scope.launch {
            delay(400)
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(snapshot.toString())
            tmp.renameTo(file)
        }
    }

    // ------------------------------------------------------------ subscriptions

    fun isSubscribed(url: String?) = url != null && subscriptions.any { sameChannel(it.url, url) }

    fun toggleSubscribe(c: ChannelRef) {
        val i = subscriptions.indexOfFirst { sameChannel(it.url, c.url) }
        if (i >= 0) subscriptions.removeAt(i) else subscriptions.add(0, c)
        save()
    }

    private fun sameChannel(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')

    // ------------------------------------------------------------ likes

    fun isLiked(v: VideoRef) = liked.any { it.id == v.id }
    fun isDisliked(v: VideoRef) = v.id in disliked

    fun toggleLike(v: VideoRef) {
        val i = liked.indexOfFirst { it.id == v.id }
        if (i >= 0) liked.removeAt(i) else { liked.add(0, v); disliked.remove(v.id) }
        save()
    }

    fun toggleDislike(v: VideoRef) {
        if (v.id in disliked) disliked.remove(v.id) else {
            disliked.add(v.id)
            liked.removeAll { it.id == v.id }
        }
        save()
    }

    // ------------------------------------------------------------ playlists

    fun createPlaylist(name: String): Playlist {
        val p = Playlist("p" + System.currentTimeMillis(), name.trim().ifEmpty { "New playlist" }, emptyList())
        playlists += p
        save()
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i >= 0) { playlists[i] = playlists[i].copy(name = name); save() }
    }

    fun deletePlaylist(id: String) {
        if (id == WATCH_LATER) return
        playlists.removeAll { it.id == id }
        save()
    }

    fun inPlaylist(id: String, v: VideoRef) = playlists.firstOrNull { it.id == id }?.items?.any { it.id == v.id } == true

    fun togglePlaylist(id: String, v: VideoRef) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = if (p.items.any { it.id == v.id }) p.copy(items = p.items.filterNot { it.id == v.id }) else p.copy(items = listOf(v) + p.items)
        save()
    }

    fun removeFromPlaylist(id: String, v: VideoRef) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i >= 0) { playlists[i] = playlists[i].copy(items = playlists[i].items.filterNot { it.id == v.id }); save() }
    }

    // ------------------------------------------------------------ history

    fun addHistory(v: VideoRef) {
        if (!Prefs.keepHistory) return
        history.removeAll { it.id == v.id }
        history.add(0, v)
        while (history.size > 500) history.removeAt(history.lastIndex)
        save()
    }

    fun removeHistory(v: VideoRef) { history.removeAll { it.id == v.id }; save() }
    fun clearHistory() { history.clear(); positions.clear(); save() }

    fun savePosition(id: String, seconds: Long, duration: Long) {
        // Forget the spot once you've basically finished.
        if (seconds < 10 || (duration > 0 && seconds > duration - 15)) positions.remove(id) else positions[id] = seconds
        save()
    }

    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        searches.remove(t)
        searches.add(0, t)
        while (searches.size > 30) searches.removeAt(searches.lastIndex)
        save()
    }

    fun removeSearch(q: String) { searches.remove(q); save() }
    fun clearSearches() { searches.clear(); save() }
}
