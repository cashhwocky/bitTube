package com.bitdroid.tube.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

/** A video as the UI (and the local library) sees it. */
data class VideoRef(
    val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumb: String?,
    val avatar: String?,
    val duration: Long, // seconds, -1 when unknown / live
    val views: Long = -1,
    val age: String? = null,
    val live: Boolean = false,
    val short: Boolean = false,
    /** Already-formatted view count from YouTube's own pages ("1.2M views"). */
    val viewsText: String? = null,
) {
    val id: String get() = videoId(url) ?: url

    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("title", title).put("channel", channel).put("channelUrl", channelUrl)
        .put("thumb", thumb).put("avatar", avatar).put("duration", duration).put("views", views)
        .put("age", age).put("live", live).put("short", short).put("viewsText", viewsText)

    companion object {
        fun fromJson(o: JSONObject) = VideoRef(
            url = o.getString("url"),
            title = o.optString("title"),
            channel = o.optString("channel"),
            channelUrl = o.optString("channelUrl").ifEmpty { null }?.takeIf { it != "null" },
            thumb = o.optString("thumb").ifEmpty { null }?.takeIf { it != "null" },
            avatar = o.optString("avatar").ifEmpty { null }?.takeIf { it != "null" },
            duration = o.optLong("duration", -1),
            views = o.optLong("views", -1),
            age = o.optString("age").ifEmpty { null }?.takeIf { it != "null" },
            live = o.optBoolean("live"),
            short = o.optBoolean("short"),
            viewsText = o.optString("viewsText").ifEmpty { null }?.takeIf { it != "null" },
        )

        fun from(item: StreamInfoItem) = VideoRef(
            url = item.url,
            title = item.name ?: "",
            channel = item.uploaderName ?: "",
            channelUrl = item.uploaderUrl,
            thumb = pickImage(item.thumbnails, 480),
            avatar = pickImage(item.uploaderAvatars, 88),
            duration = item.duration,
            views = item.viewCount,
            age = item.textualUploadDate,
            live = item.streamType == StreamType.LIVE_STREAM,
            short = runCatching { item.isShortFormContent }.getOrDefault(false) || item.url.contains("/shorts/"),
        )

        fun from(info: StreamInfo) = VideoRef(
            url = info.url,
            title = info.name ?: "",
            channel = info.uploaderName ?: "",
            channelUrl = info.uploaderUrl,
            thumb = pickImage(info.thumbnails, 480),
            avatar = pickImage(info.uploaderAvatars, 88),
            duration = info.duration,
            views = info.viewCount,
            age = info.textualUploadDate,
            live = info.streamType == StreamType.LIVE_STREAM,
            short = info.url.contains("/shorts/"),
        )
    }
}

data class ChannelRef(val url: String, val name: String, val avatar: String?, val subscribers: Long = -1) {
    fun toJson(): JSONObject = JSONObject().put("url", url).put("name", name).put("avatar", avatar).put("subs", subscribers)

    companion object {
        fun fromJson(o: JSONObject) = ChannelRef(
            o.getString("url"), o.optString("name"),
            o.optString("avatar").ifEmpty { null }?.takeIf { it != "null" }, o.optLong("subs", -1),
        )

        fun from(item: ChannelInfoItem) = ChannelRef(item.url, item.name ?: "", pickImage(item.thumbnails, 120), item.subscriberCount)
    }
}

/** Something a list can show: a video or a channel. */
sealed interface Entry {
    data class Video(val v: VideoRef) : Entry
    data class Channel(val c: ChannelRef) : Entry
}

fun InfoItem.toEntry(): Entry? = when (this) {
    is StreamInfoItem -> Entry.Video(VideoRef.from(this))
    is ChannelInfoItem -> Entry.Channel(ChannelRef.from(this))
    else -> null
}

/** Smallest image at least [target] px tall, else the biggest there is. */
fun pickImage(images: List<Image>?, target: Int): String? {
    if (images.isNullOrEmpty()) return null
    val known = images.filter { it.height > 0 }.sortedBy { it.height }
    val pick = known.firstOrNull { it.height >= target } ?: known.lastOrNull() ?: images.last()
    return pick.url
}

fun videoId(url: String): String? {
    val patterns = listOf(
        Regex("[?&]v=([A-Za-z0-9_-]{11})"),
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("/shorts/([A-Za-z0-9_-]{11})"),
        Regex("/live/([A-Za-z0-9_-]{11})"),
        Regex("/embed/([A-Za-z0-9_-]{11})"),
    )
    return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}

fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

// ---------------------------------------------------------------- formatting

fun compact(n: Long): String {
    if (n < 0) return ""
    fun fmt(v: Double, s: String): String {
        val str = if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0") else v.toLong().toString()
        return str + s
    }
    return when {
        n >= 1_000_000_000 -> fmt(n / 1e9, "B")
        n >= 1_000_000 -> fmt(n / 1e6, "M")
        n >= 1_000 -> fmt(n / 1e3, "K")
        else -> n.toString()
    }
}

fun clock(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun VideoRef.meta(): String = listOfNotNull(
    channel.takeIf { it.isNotBlank() },
    if (views >= 0) "${compact(views)} views" else viewsText,
    age,
).joinToString(" · ")
