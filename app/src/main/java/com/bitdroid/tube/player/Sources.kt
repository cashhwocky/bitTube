package com.bitdroid.tube.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bitdroid.tube.data.Net
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/** Turns a StreamInfo into something ExoPlayer can play, at a chosen quality. */
object Sources {
    private val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    /** googlevideo checks that the user agent matches the YouTube client the stream was made for. */
    val dataSource: DataSource.Factory = ResolvingDataSource.Factory(http) { spec ->
        val url = spec.uri.toString()
        val headers = mutableMapOf("User-Agent" to userAgentFor(url))
        if (url.contains("c=WEB")) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        }
        spec.withAdditionalHeaders(headers)
    }

    private fun clientOf(url: String): String = Regex("[?&]c=([A-Z_]+)").find(url)?.groupValues?.get(1) ?: ""

    private fun helperUa(method: String): String? = runCatching {
        val c = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper")
        c.getMethod(method, org.schabi.newpipe.extractor.localization.Localization::class.java).invoke(null, null) as String
    }.getOrNull()

    private fun userAgentFor(url: String): String = when (val c = clientOf(url)) {
        "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        "ANDROID" -> helperUa("getAndroidUserAgent") ?: "com.google.android.youtube/19.28.35 (Linux; U; Android 15; US) gzip"
        "IOS" -> helperUa("getIosUserAgent") ?: "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
        else -> if (c.startsWith("TVHTML5")) "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version" else Net.BROWSER_UA
    }

    private val playableVideo = setOf(MediaFormat.MPEG_4, MediaFormat.WEBM)
    private val playableAudio = setOf(MediaFormat.M4A, MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS, MediaFormat.OPUS)

    private fun videoOnly(info: StreamInfo) = info.videoOnlyStreams.filter { it.format in playableVideo && it.height > 0 }
    private fun muxed(info: StreamInfo) = info.videoStreams.filter { it.format in playableVideo }

    /** Heights you can pick, best first. */
    fun qualities(info: StreamInfo): List<Int> =
        (videoOnly(info).map { it.height } + muxed(info).map { it.height }).filter { it > 0 }.distinct().sortedDescending()

    private fun bestAudio(info: StreamInfo): AudioStream? {
        val all = info.audioStreams.filter { it.format in playableAudio }
        // Original language track first (not a dub or audio description), AAC preferred for compatibility.
        val original = all.filter { runCatching { it.audioTrackType }.getOrNull()?.name.let { n -> n == null || n == "ORIGINAL" } }.ifEmpty { all }
        return original.maxWithOrNull(compareBy<AudioStream>({ if (it.format == MediaFormat.M4A) 1 else 0 }, { it.averageBitrate }))
    }

    /** Picks streams at or under [cap] px (0 = best there is). Returns the source and the height it plays at. */
    fun build(info: StreamInfo, cap: Int): Pair<MediaSource, Int> {
        val id = info.id
        // Live: HLS is the reliable way in.
        if (!info.hlsUrl.isNullOrEmpty()) {
            val src = HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.Builder().setUri(info.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).setMediaId(id).build())
            return src to 0
        }
        val limit = if (cap <= 0) Int.MAX_VALUE else cap
        val audio = bestAudio(info)
        val vids = videoOnly(info)
        val video = vids.filter { it.height <= limit }.maxWithOrNull(videoOrder)
            ?: vids.minByOrNull { it.height }
        if (video != null && audio != null) {
            val dur = info.duration
            return MergingMediaSource(true, single(video, dur, id), single(audio, dur, id)) to video.height
        }
        val m = muxed(info)
        val mux = m.filter { it.height <= limit }.maxWithOrNull(videoOrder) ?: m.firstOrNull()
            ?: throw IllegalStateException("No playable stream")
        return single(mux, info.duration, id) to mux.height
    }

    // Higher resolution, then higher frame rate, then MP4 over WebM.
    private val videoOrder = compareBy<VideoStream>({ it.height }, { it.fps }, { if (it.format == MediaFormat.MPEG_4) 1 else 0 })

    private fun single(stream: Stream, durationSec: Long, id: String): MediaSource {
        val item = MediaItem.Builder().setUri(stream.content).setMediaId(id).build()
        when (stream.deliveryMethod) {
            DeliveryMethod.HLS -> return HlsMediaSource.Factory(dataSource).createMediaSource(item)
            DeliveryMethod.DASH -> if (stream.isUrl) return DashMediaSource.Factory(dataSource).createMediaSource(item)
            else -> Unit
        }
        // YouTube's adaptive streams play best through a small DASH manifest (ranged segment requests).
        val itag = when (stream) {
            is VideoStream -> stream.itagItem
            is AudioStream -> stream.itagItem
            else -> null
        }
        if (itag != null && durationSec > 0) {
            runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itag, durationSec)
                val manifest = DashManifestParser().parse(Uri.parse(stream.content), xml.byteInputStream())
                return DashMediaSource.Factory(dataSource).createMediaSource(manifest, item)
            }
        }
        return ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
    }
}
