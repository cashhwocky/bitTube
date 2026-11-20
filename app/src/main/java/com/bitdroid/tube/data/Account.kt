package com.bitdroid.tube.data

import android.content.Context
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/**
 * Your YouTube account, the same way AlexLMP does it: sign in on Google's own page in a WebView,
 * keep the youtube.com cookies, and call YouTube's web API (InnerTube) with them.
 * That gives the real For you page, your subscriptions feed, likes, subscribing, playlists and history.
 * Playback still goes through NewPipe; this is only for your account.
 */
object Account {
    private const val YT = "https://www.youtube.com"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3D%252F"

    private lateinit var app: Context
    private fun prefs() = app.getSharedPreferences("yt_account", Context.MODE_PRIVATE)

    var signedIn by mutableStateOf(false)
        private set
    var name by mutableStateOf<String?>(null)
        private set
    var handle by mutableStateOf<String?>(null)
        private set
    var photo by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        app = context.applicationContext
        signedIn = cookies() != null
        name = prefs().getString("name", null)
        handle = prefs().getString("handle", null)
        photo = prefs().getString("photo", null)
    }

    fun cookies(): String? = prefs().getString("cookies", null)?.takeIf { it.contains("SAPISID=") }

    fun saveCookies(c: String) {
        prefs().edit().putString("cookies", c).apply()
        signedIn = true
    }

    fun signOut() {
        prefs().edit().clear().apply()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        signedIn = false
        name = null
        handle = null
        photo = null
    }

    // ------------------------------------------------------------ InnerTube

    private fun sapisidHash(cookies: String): String? {
        val sapisid = Regex("(?:^|;\\s*)SAPISID=([^;]+)").find(cookies)?.groupValues?.get(1)
            ?: Regex("(?:^|;\\s*)__Secure-3PAPISID=([^;]+)").find(cookies)?.groupValues?.get(1)
            ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$ts $sapisid $YT".toByteArray()).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$digest"
    }

    private fun call(endpoint: String, body: JSONObject): JSONObject {
        val c = cookies() ?: throw IOException("Not signed in to YouTube")
        val locale = Locale.getDefault()
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", locale.language.ifEmpty { "en" })
            .put("gl", locale.country.takeIf { it.length == 2 } ?: "US")
        body.put("context", JSONObject().put("client", client).put("user", JSONObject()))
        val req = okhttp3.Request.Builder()
            .url("$YT/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", DESKTOP_UA)
            .header("Origin", YT)
            .header("X-Origin", YT)
            .header("Referer", "$YT/")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("X-Goog-AuthUser", "0")
            .header("Cookie", c)
            .apply { sapisidHash(c)?.let { header("Authorization", it) } }
            .build()
        Net.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw IOException("YouTube rejected the sign-in (HTTP ${r.code}). Sign out and sign in again.")
            if (!r.isSuccessful) throw IOException("YouTube HTTP ${r.code}: ${text.take(200)}")
            return JSONObject(text)
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ------------------------------------------------------------ profile

    /** Your name and photo for the header. */
    suspend fun refreshProfile() = io {
        runCatching {
            val r = call("account/account_menu", JSONObject())
            val h = all(r, "activeAccountHeaderRenderer").firstOrNull() ?: return@runCatching
            val n = text(h.optJSONObject("accountName"))
            val hd = text(h.optJSONObject("channelHandle"))
            val p = thumb(h.optJSONObject("accountPhoto"))
            prefs().edit().putString("name", n).putString("handle", hd).putString("photo", p).apply()
            name = n
            handle = hd
            photo = p
        }
    }

    // ------------------------------------------------------------ feeds

    private fun browsePaged(browseId: String): Paged {
        val r = call("browse", JSONObject().put("browseId", browseId))
        return page(r) { token -> call("browse", JSONObject().put("continuation", token)) }
    }

    private fun page(r: JSONObject, more: (String) -> JSONObject): Paged {
        val videos = parseVideos(r)
        val token = continuation(r)
        val loader: (suspend (Page) -> Paged)? = if (token == null) null else { p -> io { page(more(p.url), more) } }
        return Paged(videos.map { Entry.Video(it) }, token?.let { Page(it) }, loader)
    }

    /** Your real YouTube home: the For you page. */
    suspend fun home(): Paged = io { browsePaged("FEwhat_to_watch") }

    /** New videos from channels you're subscribed to on YouTube. */
    suspend fun subscriptionFeed(): Paged = io { browsePaged("FEsubscriptions") }

    /** Any list page: VLLL (liked), VLWL (watch later), FEhistory, VL<playlist id>. */
    suspend fun list(browseId: String): Paged = io { browsePaged(browseId) }

    /** Everyone you're subscribed to on YouTube. */
    suspend fun channels(): List<ChannelRef> = io {
        val out = mutableListOf<ChannelRef>()
        var r = call("browse", JSONObject().put("browseId", "FEchannels"))
        repeat(20) {
            all(r, "channelRenderer").forEach { c ->
                val id = c.optString("channelId").ifEmpty { return@forEach }
                out += ChannelRef("$YT/channel/$id", text(c.optJSONObject("title")) ?: "", thumb(c.optJSONObject("thumbnail")))
            }
            val token = continuation(r) ?: return@io out.distinctBy { it.url }
            r = call("browse", JSONObject().put("continuation", token))
        }
        out.distinctBy { it.url }
    }

    data class YtPlaylist(val id: String, val title: String, val thumb: String?, val count: String?)

    /** Your saved and created playlists. */
    suspend fun playlists(): List<YtPlaylist> = io {
        val out = mutableListOf<YtPlaylist>()
        for (feed in listOf("FEplaylist_aggregation", "FElibrary")) {
            val r = runCatching { call("browse", JSONObject().put("browseId", feed)) }.getOrNull() ?: continue
            all(r, "lockupViewModel").forEach { l ->
                if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_PLAYLIST") return@forEach
                val id = l.optString("contentId").ifEmpty { return@forEach }
                val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                val title = meta?.optJSONObject("title")?.optString("content") ?: return@forEach
                val img = firstString(l.optJSONObject("contentImage"), "url")
                val badge = all(l, "thumbnailBadgeViewModel").firstOrNull()?.optString("text")
                out += YtPlaylist(id, title, img, badge)
            }
            all(r, "gridPlaylistRenderer").forEach { g ->
                val id = g.optString("playlistId").ifEmpty { return@forEach }
                out += YtPlaylist(id, text(g.optJSONObject("title")) ?: "", thumb(g.optJSONObject("thumbnail")), text(g.optJSONObject("videoCountShortText")))
            }
            if (out.isNotEmpty()) break
        }
        out.filter { it.id != "LL" && it.id != "WL" }.distinctBy { it.id }
    }

    // ------------------------------------------------------------ shorts

    private var reelToken: String? = null

    private fun shortRef(id: String, title: String?, views: String?) = VideoRef(
        url = "$YT/shorts/$id", title = title ?: "", channel = "", channelUrl = null,
        thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg", avatar = null, duration = -1, viewsText = views, short = true,
    )

    /** Shorts on a page: the new shortsLockupViewModel and the older reelItemRenderer. */
    private fun parseShorts(r: JSONObject): List<VideoRef> {
        val found = mutableListOf<Pair<String, JSONObject>>()
        collect(r, setOf("shortsLockupViewModel", "reelItemRenderer"), found)
        return found.mapNotNull { (k, o) ->
            if (k == "reelItemRenderer") {
                val id = o.optString("videoId").ifEmpty { return@mapNotNull null }
                shortRef(id, text(o.optJSONObject("headline")), text(o.optJSONObject("viewCountText")))
            } else {
                val id = firstString(o.optJSONObject("onTap"), "videoId") ?: firstString(o, "videoId") ?: return@mapNotNull null
                val meta = o.optJSONObject("overlayMetadata")
                shortRef(id, meta?.optJSONObject("primaryText")?.optString("content"), meta?.optJSONObject("secondaryText")?.optString("content"))
            }
        }
    }

    private fun sequence(token: String): JSONObject {
        val a = call("reel/reel_watch_sequence", JSONObject().put("sequenceParams", token))
        if ((a.optJSONArray("entries")?.length() ?: 0) > 0) return a
        return call("reel/reel_watch_sequence", JSONObject().put("continuation", token))
    }

    /**
     * Your personal Shorts feed: the same endless sequence the YouTube app plays, seeded from the
     * Shorts YouTube recommends on your home page. Falls back to just those home Shorts.
     */
    suspend fun shorts(): List<VideoRef> = io {
        val out = mutableListOf<VideoRef>()
        runCatching {
            if (reelToken == null) {
                val home = call("browse", JSONObject().put("browseId", "FEwhat_to_watch"))
                out += parseShorts(home)
                val seed = out.firstOrNull()?.id ?: return@runCatching
                val item = call(
                    "reel/reel_item_watch",
                    JSONObject().put("playerRequest", JSONObject().put("videoId", seed)).put("params", "CAUwAg%3D%3D").put("disablePlayerResponse", true),
                )
                reelToken = item.optString("sequenceContinuation").ifEmpty { null } ?: firstString(item, "sequenceContinuation")
            }
            val t = reelToken ?: return@runCatching
            val seq = sequence(t)
            val entries = seq.optJSONArray("entries")
            if (entries != null) for (i in 0 until entries.length()) {
                val id = firstString(entries.optJSONObject(i), "videoId") ?: continue
                out += shortRef(id, null, null)
            }
            reelToken = continuation(seq) ?: seq.optString("continuation").ifEmpty { null }
        }
        if (out.isEmpty()) {
            // No sequence: take Shorts from the home and subscriptions pages instead.
            runCatching { out += parseShorts(call("browse", JSONObject().put("browseId", "FEwhat_to_watch"))) }
            runCatching { out += parseShorts(call("browse", JSONObject().put("browseId", "FEsubscriptions"))) }
        }
        out.distinctBy { it.id }
    }

    fun resetShorts() { reelToken = null }

    // ------------------------------------------------------------ actions

    fun channelId(url: String?): String? = url?.let { Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(it)?.groupValues?.get(1) }

    suspend fun like(id: String) = io { call("like/like", JSONObject().put("target", JSONObject().put("videoId", id))) }
    suspend fun dislike(id: String) = io { call("like/dislike", JSONObject().put("target", JSONObject().put("videoId", id))) }
    suspend fun removeLike(id: String) = io { call("like/removelike", JSONObject().put("target", JSONObject().put("videoId", id))) }

    suspend fun subscribe(channelId: String) = io { call("subscription/subscribe", JSONObject().put("channelIds", JSONArray().put(channelId))) }
    suspend fun unsubscribe(channelId: String) = io { call("subscription/unsubscribe", JSONObject().put("channelIds", JSONArray().put(channelId))) }

    data class SaveOption(val id: String, val title: String, val contains: Boolean)

    /** Your playlists with a tick for the ones already holding [videoId] (includes Watch later). */
    suspend fun saveOptions(videoId: String): List<SaveOption> = io {
        val r = call("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)))
        all(r, "playlistAddToOptionRenderer").mapNotNull { o ->
            val id = o.optString("playlistId").ifEmpty { return@mapNotNull null }
            SaveOption(id, text(o.optJSONObject("title")) ?: id, o.optString("containsSelectedVideos") == "ALL")
        }
    }

    suspend fun addTo(playlistId: String, videoId: String) = io {
        call("browse/edit_playlist", JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId))))
    }

    suspend fun removeFrom(playlistId: String, videoId: String) = io {
        call("browse/edit_playlist", JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId))))
    }

    suspend fun createPlaylist(title: String, videoId: String) = io {
        call("playlist/create", JSONObject().put("title", title).put("privacyStatus", "PRIVATE").put("videoIds", JSONArray().put(videoId)))
    }

    /**
     * Tells YouTube you watched [videoId], so it lands in your YouTube history and your For you page
     * learns from what you watch in bitTube (the same "mark watched" ping the website sends).
     */
    suspend fun markWatched(videoId: String) = io {
        runCatching {
            val r = call("player", JSONObject().put("videoId", videoId))
            val base = r.optJSONObject("playbackTracking")?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl") ?: return@runCatching
            val cpn = buildString { val abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"; repeat(16) { append(abc.random()) } }
            val url = base.replace(Regex("[?&]ver=[^&]*"), "") + (if (base.contains('?')) "&" else "?") + "ver=2&cpn=$cpn"
            val c = cookies() ?: return@runCatching
            val req = okhttp3.Request.Builder().url(url).header("User-Agent", DESKTOP_UA).header("Cookie", c)
                .apply { sapisidHash(c)?.let { header("Authorization", it) } }.build()
            Net.client.newCall(req).execute().close()
        }
    }

    // ------------------------------------------------------------ parsing

    private fun collect(node: Any?, keys: Set<String>, out: MutableList<Pair<String, JSONObject>>) {
        when (node) {
            is JSONObject -> {
                val it = node.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = node.opt(k)
                    if (k in keys && v is JSONObject) out.add(k to v) else collect(v, keys, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), keys, out)
        }
    }

    private fun all(node: Any?, key: String): List<JSONObject> =
        mutableListOf<Pair<String, JSONObject>>().also { collect(node, setOf(key), it) }.map { it.second }

    private fun firstString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.opt(key)
                if (direct is String && direct.isNotEmpty()) return direct
                val it = node.keys()
                while (it.hasNext()) firstString(node.opt(it.next()), key)?.let { s -> return s }
            }
            is JSONArray -> for (i in 0 until node.length()) firstString(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun text(o: JSONObject?): String? {
        if (o == null) return null
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        o.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifEmpty { null }
    }

    private fun fixUrl(u: String?) = when {
        u == null -> null
        u.startsWith("//") -> "https:$u"
        else -> u
    }

    private fun thumb(o: JSONObject?): String? {
        val arr = o?.optJSONArray("thumbnails") ?: o?.optJSONArray("sources") ?: return fixUrl(firstString(o, "url"))
        return fixUrl(arr.optJSONObject(arr.length() - 1)?.optString("url"))
    }

    private fun seconds(s: String?): Long {
        if (s.isNullOrBlank()) return -1
        val parts = s.trim().split(':').map { it.toLongOrNull() ?: return -1 }
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun channelFrom(o: JSONObject?): String? =
        firstString(o, "browseId")?.takeIf { it.startsWith("UC") }?.let { "$YT/channel/$it" }

    private fun continuation(r: JSONObject): String? {
        val cmds = all(r, "continuationCommand").mapNotNull { it.optString("token").ifEmpty { null } }
        if (cmds.isNotEmpty()) return cmds.last()
        return all(r, "nextContinuationData").mapNotNull { it.optString("continuation").ifEmpty { null } }.lastOrNull()
    }

    /** Pulls every video out of a YouTube page, old renderers and new "lockup" view models alike. */
    private fun parseVideos(r: JSONObject): List<VideoRef> {
        val found = mutableListOf<Pair<String, JSONObject>>()
        collect(r, setOf("videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer", "lockupViewModel"), found)
        return found.mapNotNull { (k, o) -> runCatching { if (k == "lockupViewModel") lockup(o) else renderer(o) }.getOrNull() }.distinctBy { it.id }
    }

    private fun renderer(o: JSONObject): VideoRef? {
        val id = o.optString("videoId").ifEmpty { return null }
        val owner = o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText") ?: o.optJSONObject("longBylineText")
        val live = o.optJSONArray("badges")?.toString()?.contains("LIVE") == true ||
            o.optJSONArray("thumbnailOverlays")?.toString()?.contains("\"LIVE\"") == true
        val length = text(o.optJSONObject("lengthText"))
            ?: firstString(o.optJSONArray("thumbnailOverlays"), "simpleText")?.takeIf { it.contains(':') }
        val info = text(o.optJSONObject("videoInfo"))?.split(" • ", " · ")
        return VideoRef(
            url = watchUrl(id),
            title = text(o.optJSONObject("title")) ?: "",
            channel = text(owner) ?: "",
            channelUrl = channelFrom(owner),
            thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            avatar = thumb(o.optJSONObject("channelThumbnailSupportedRenderers")?.optJSONObject("channelThumbnailWithLinkRenderer")?.optJSONObject("thumbnail"))
                ?: thumb(o.optJSONObject("channelThumbnail")),
            duration = o.optString("lengthSeconds").toLongOrNull() ?: seconds(length),
            viewsText = text(o.optJSONObject("shortViewCountText")) ?: text(o.optJSONObject("viewCountText")) ?: info?.getOrNull(0),
            age = text(o.optJSONObject("publishedTimeText")) ?: info?.getOrNull(1),
            live = live,
        )
    }

    private fun lockup(o: JSONObject): VideoRef? {
        if (o.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = o.optString("contentId").ifEmpty { return null }
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        // Rows look like: [channel] / [views · age]; read every text part in order.
        val parts = mutableListOf<String>()
        val rowArr = meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        if (rowArr != null) for (i in 0 until rowArr.length()) {
            val ps = rowArr.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until ps.length()) ps.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        val badge = all(o.optJSONObject("contentImage"), "thumbnailBadgeViewModel").firstOrNull()?.optString("text")
        val live = badge?.contains("LIVE", true) == true
        val avatar = all(meta.optJSONObject("image"), "avatarViewModel").firstOrNull()?.let { thumb(it.optJSONObject("image")) }
        return VideoRef(
            url = watchUrl(id),
            title = meta.optJSONObject("title")?.optString("content") ?: "",
            channel = parts.getOrNull(0) ?: "",
            channelUrl = channelFrom(meta.optJSONObject("metadata")) ?: channelFrom(meta.optJSONObject("image")),
            thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            avatar = avatar,
            duration = if (live) -1 else seconds(badge),
            viewsText = parts.getOrNull(1),
            age = parts.getOrNull(2),
            live = live,
        )
    }
}

/**
 * Likes, dislikes, subscriptions, Watch later and history: saved on the phone always, and sent to
 * your YouTube account too when you're signed in.
 */
object Sync {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context

    fun init(context: Context) { app = context.applicationContext }

    private fun remote(what: String, block: suspend () -> Unit) {
        if (!Account.signedIn) return
        scope.launch {
            runCatching { block() }.onFailure { e ->
                android.widget.Toast.makeText(app, "YouTube $what failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun like(v: VideoRef) {
        val now = !Library.isLiked(v)
        Library.toggleLike(v)
        remote("like") { if (now) Account.like(v.id) else Account.removeLike(v.id) }
    }

    fun dislike(v: VideoRef) {
        val now = !Library.isDisliked(v)
        Library.toggleDislike(v)
        remote("dislike") { if (now) Account.dislike(v.id) else Account.removeLike(v.id) }
    }

    fun subscribe(c: ChannelRef) {
        val now = !Library.isSubscribed(c.url)
        Library.toggleSubscribe(c)
        val id = Account.channelId(c.url) ?: return
        remote("subscribe") { if (now) Account.subscribe(id) else Account.unsubscribe(id) }
    }

    fun watchLater(v: VideoRef) {
        val now = !Library.inPlaylist(Library.WATCH_LATER, v)
        Library.togglePlaylist(Library.WATCH_LATER, v)
        remote("Watch later") { if (now) Account.addTo("WL", v.id) else Account.removeFrom("WL", v.id) }
    }

    fun watched(v: VideoRef) {
        if (Prefs.syncHistory) remote("history") { Account.markWatched(v.id) }
    }
}
