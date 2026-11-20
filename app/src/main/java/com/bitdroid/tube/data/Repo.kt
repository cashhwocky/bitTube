package com.bitdroid.tube.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** A page of results plus a way to load the next one. */
class Paged(val items: List<Entry>, private val nextPage: Page?, private val loader: (suspend (Page) -> Paged)?) {
    val hasMore: Boolean get() = nextPage != null && loader != null
    suspend fun more(): Paged? = if (nextPage != null && loader != null) loader.invoke(nextPage) else null
}

/** Everything bitTube reads from YouTube, through NewPipeExtractor (no API key, no account). */
object Repo {
    val yt: StreamingService get() = ServiceList.YouTube

    fun init() {
        val locale = Locale.getDefault()
        NewPipe.init(Net, Localization.fromLocale(locale), ContentCountry(locale.country.ifEmpty { "US" }))
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun List<InfoItem>.entries(): List<Entry> = mapNotNull { it.toEntry() }

    // ------------------------------------------------------------ search

    enum class Filter(val label: String, val key: String?) { ALL("All", null), VIDEOS("Videos", "videos"), CHANNELS("Channels", "channels") }

    suspend fun search(query: String, filter: Filter = Filter.ALL): Paged = io {
        val handler = yt.searchQHFactory.fromQuery(query, listOfNotNull(filter.key), "")
        val info = SearchInfo.getInfo(yt, handler)
        Paged(info.relatedItems.entries(), info.nextPage, searchMore(handler))
    }

    private fun searchMore(handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler): suspend (Page) -> Paged = { page ->
        io {
            val p = SearchInfo.getMoreItems(yt, handler, page)
            Paged(p.items.entries(), p.nextPage, searchMore(handler))
        }
    }

    suspend fun suggestions(query: String): List<String> =
        if (query.isBlank()) emptyList() else io { runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList()) }

    // ------------------------------------------------------------ kiosks ("charts")

    fun kiosks(): List<String> = runCatching { yt.kioskList.availableKiosks.toList().sorted() }.getOrDefault(emptyList())

    fun kioskLabel(id: String): String =
        id.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.titlecase() }

    suspend fun kiosk(id: String): Paged = io {
        val url = yt.kioskList.getListLinkHandlerFactoryByType(id).fromId(id).url
        val info = KioskInfo.getInfo(yt, url)
        Paged(info.relatedItems.mapNotNull { (it as InfoItem).toEntry() }, info.nextPage, kioskMore(url))
    }

    private fun kioskMore(url: String): suspend (Page) -> Paged = { page ->
        io {
            val p = KioskInfo.getMoreItems(yt, url, page)
            Paged(p.items.mapNotNull { (it as InfoItem).toEntry() }, p.nextPage, kioskMore(url))
        }
    }

    // ------------------------------------------------------------ videos

    private val streamCache = LruCache<String, StreamInfo>(40)

    suspend fun stream(url: String): StreamInfo {
        val key = videoId(url) ?: url
        streamCache.get(key)?.let { return it }
        val info = io { StreamInfo.getInfo(yt, url) }
        streamCache.put(key, info)
        return info
    }

    /** Like and dislike counts from Return YouTube Dislike (YouTube hides dislikes). */
    suspend fun votes(id: String): Pair<Long, Long>? = io {
        runCatching {
            val o = JSONObject(Net.getText("https://returnyoutubedislikeapi.com/votes?videoId=$id"))
            o.optLong("likes", -1) to o.optLong("dislikes", -1)
        }.getOrNull()
    }

    // ------------------------------------------------------------ channels

    class Channel(val info: ChannelInfo, val tabs: List<Pair<String, ListLinkHandler>>)

    suspend fun channel(url: String): Channel = io {
        val info = ChannelInfo.getInfo(yt, url)
        val tabs = info.tabs.mapNotNull { t ->
            when (t.contentFilters.firstOrNull()) {
                ChannelTabs.VIDEOS -> "Videos" to t
                ChannelTabs.SHORTS -> "Clips" to t
                ChannelTabs.LIVESTREAMS -> "Live" to t
                else -> null
            }
        }
        Channel(info, tabs)
    }

    suspend fun channelTab(tab: ListLinkHandler): Paged = io {
        val info = ChannelTabInfo.getInfo(yt, tab)
        Paged(info.relatedItems.entries(), info.nextPage, tabMore(tab))
    }

    private fun tabMore(tab: ListLinkHandler): suspend (Page) -> Paged = { page ->
        io {
            val p = ChannelTabInfo.getMoreItems(yt, tab, page)
            Paged(p.items.entries(), p.nextPage, tabMore(tab))
        }
    }

    // ------------------------------------------------------------ feed

    private fun StreamInfoItem.time(): Long =
        runCatching { uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }.getOrNull() ?: 0L

    /** Newest uploads from everyone you're subscribed to, newest first. */
    suspend fun feed(channels: List<ChannelRef>): List<VideoRef> = coroutineScope {
        val gate = Semaphore(6)
        channels.map { c ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching {
                        val ex = yt.getFeedExtractor(c.url)
                        if (ex != null) {
                            ex.fetchPage()
                            ex.initialPage.items.map { it to it.time() }
                        } else {
                            val ch = ChannelInfo.getInfo(yt, c.url)
                            val tab = ch.tabs.firstOrNull { it.contentFilters.firstOrNull() == ChannelTabs.VIDEOS } ?: return@runCatching emptyList()
                            ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>().map { it to it.time() }
                        }
                    }.getOrDefault(emptyList()).map { (item, t) ->
                        // Feed entries don't always carry the avatar; use the one we saved.
                        val v = VideoRef.from(item)
                        Triple(if (v.avatar == null) v.copy(avatar = c.avatar) else v, t, item)
                    }
                }
            }
        }.awaitAll().flatten()
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
    }


    // ------------------------------------------------------------ for you

    /** Home's default list: new uploads from your subscriptions mixed with videos related to what you watched. */
    suspend fun forYou(): Paged = coroutineScope {
        val subs = Library.subscriptions.toList()
        val recent = Library.history.filter { !it.short }.take(3)
        val fromSubs = async { if (subs.isEmpty()) emptyList() else runCatching { feed(subs.take(20)).take(24) }.getOrDefault(emptyList()) }
        val related = recent.map { v ->
            async {
                runCatching { stream(v.url).relatedItems.mapNotNull { (it.toEntry() as? Entry.Video)?.v } }.getOrDefault(emptyList()).take(10)
            }
        }
        val watched = Library.history.map { it.id }.toHashSet()
        val a = fromSubs.await()
        val b = related.awaitAll().flatten().filter { it.id !in watched && !it.short }
        val mixed = buildList {
            val n = maxOf(a.size, b.size)
            for (i in 0 until n) {
                a.getOrNull(i)?.let(::add)
                b.getOrNull(i)?.let(::add)
            }
        }.distinctBy { it.id }
        if (mixed.isEmpty()) search("popular videos today", Filter.VIDEOS)
        else Paged(mixed.map { Entry.Video(it) }, null, null)
    }

    // ------------------------------------------------------------ clips (short vertical videos)

    private val clipTopics = listOf("", "funny", "music", "gaming", "tech", "satisfying", "cooking", "sports", "animals", "science", "art", "travel")
    private var clipRound = 0

    /** A shuffled batch of clips: Shorts from channels you follow, mixed with popular ones. */
    suspend fun clips(): List<VideoRef> = coroutineScope {
        val round = clipRound++
        val fromSubs = async(Dispatchers.IO) {
            Library.subscriptions.toList().shuffled().take(4).flatMap { c ->
                runCatching {
                    val ch = ChannelInfo.getInfo(yt, c.url)
                    val tab = ch.tabs.firstOrNull { it.contentFilters.firstOrNull() == ChannelTabs.SHORTS } ?: return@runCatching emptyList()
                    ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>()
                        .take(6).map { VideoRef.from(it).copy(short = true, channel = it.uploaderName ?: c.name, channelUrl = it.uploaderUrl ?: c.url, avatar = c.avatar) }
                }.getOrDefault(emptyList())
            }
        }
        val topics = clipTopics.shuffled().take(2).plus(if (round == 0) listOf("") else emptyList()).distinct()
        val fromSearch = topics.map { t ->
            async(Dispatchers.IO) {
                runCatching {
                    val q = if (t.isEmpty()) "#shorts" else "#shorts $t"
                    val info = SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery(q, listOf("videos"), ""))
                    info.relatedItems.filterIsInstance<StreamInfoItem>().map { VideoRef.from(it) }
                        .filter { it.short || it.duration in 1..180 }
                }.getOrDefault(emptyList())
            }
        }
        (fromSubs.await() + fromSearch.awaitAll().flatten()).distinctBy { it.id }.shuffled()
    }
}
