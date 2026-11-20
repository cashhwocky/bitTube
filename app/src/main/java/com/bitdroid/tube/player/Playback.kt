package com.bitdroid.tube.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.bitdroid.tube.data.Entry
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Prefs
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.toEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

private fun newPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .setSeekBackIncrementMs(10_000)
    .setSeekForwardIncrementMs(10_000)
    .build()

private fun friendly(e: Throwable): String {
    val m = e.message.orEmpty()
    return when {
        m.contains("age", true) && m.contains("restrict", true) -> "This video is age-restricted."
        m.contains("private", true) -> "This video is private."
        m.contains("sign in", true) || m.contains("bot", true) -> "YouTube asked to confirm you're not a bot. Try again in a bit."
        m.contains("unavailable", true) -> "This video isn't available."
        e is java.io.IOException -> "Couldn't connect. Are you online?"
        else -> "Couldn't play this video."
    }
}

/** The main video player. Lives as long as the app process so the mini player survives screens. */
object Playback {
    lateinit var player: ExoPlayer
        private set
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var video by mutableStateOf<VideoRef?>(null)
        private set
    var info by mutableStateOf<StreamInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Full watch screen vs. the floating mini player. */
    var expanded by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var qualities by mutableStateOf(listOf<Int>())
        private set
    /** Quality the user picked for this video; 0 = automatic (from Settings). */
    var chosenQuality by mutableIntStateOf(0)
        private set
    var playingHeight by mutableIntStateOf(0)
        private set
    var aspect by mutableStateOf(16f / 9f)
        private set
    var votes by mutableStateOf<Pair<Long, Long>?>(null)
        private set
    val related: List<VideoRef> get() = info?.relatedItems?.mapNotNull { (it.toEntry() as? Entry.Video)?.v } ?: emptyList()

    private var loadJob: Job? = null

    fun init(context: Context) {
        app = context.applicationContext
        player = newPlayer(app)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    savePosition()
                    if (Prefs.autoplay) related.firstOrNull()?.let { open(it) }
                }
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlayerError(e: PlaybackException) {
                error = if (e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) "YouTube refused the stream. Try another quality." else "Playback stopped: ${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}"
            }
        })
        // Remember where you are every few seconds.
        scope.launch {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) savePosition()
            }
        }
    }

    fun open(v: VideoRef, expand: Boolean = true) {
        savePosition()
        ClipPlayback.pause()
        video = v
        info = null
        votes = null
        error = null
        qualities = emptyList()
        chosenQuality = 0
        aspect = 16f / 9f
        if (expand) expanded = true
        loading = true
        player.stop()
        player.clearMediaItems()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val i = Repo.stream(v.url)
                info = i
                val full = VideoRef.from(i).let { r -> r.copy(avatar = r.avatar ?: v.avatar, thumb = r.thumb ?: v.thumb) }
                video = full
                qualities = Sources.qualities(i)
                Library.addHistory(full)
                com.bitdroid.tube.data.Sync.watched(full)
                start(i, Library.positions[full.id] ?: 0L)
                launch { votes = Repo.votes(full.id) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    private fun start(i: StreamInfo, fromSec: Long) {
        val cap = if (chosenQuality > 0) chosenQuality else Prefs.qualityNow(app)
        val (src, h) = Sources.build(i, cap)
        playingHeight = h
        player.setMediaSource(src, fromSec * 1000)
        player.playbackParameters = PlaybackParameters(Prefs.speed)
        player.prepare()
        player.play()
    }

    fun setQuality(height: Int) {
        val i = info ?: return
        chosenQuality = height
        error = null
        val pos = player.currentPosition / 1000
        val wasPlaying = player.playWhenReady
        runCatching { start(i, pos) }.onFailure { error = friendly(it) }
        player.playWhenReady = wasPlaying
    }

    fun setSpeed(s: Float) {
        Prefs.setSpeed(s)
        player.playbackParameters = PlaybackParameters(s)
    }

    fun retry() { video?.let { open(it, expand = expanded) } }

    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }

    fun seekBy(seconds: Int) {
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + seconds * 1000L).coerceIn(0, dur))
    }

    fun close() {
        savePosition()
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        video = null
        info = null
        expanded = false
        fullscreen = false
    }

    fun savePosition() {
        val v = video ?: return
        if (player.duration <= 0) return
        Library.savePosition(v.id, player.currentPosition / 1000, player.duration / 1000)
    }
}

/**
 * Clips play from a small pool of ready-to-go players: the clip on screen plus the next two (and the
 * one before) are already fetched, prepared and buffered, so swiping starts the next one instantly.
 */
object ClipPlayback {
    /** One preloaded clip. */
    class Slot(val id: String, val player: ExoPlayer) {
        var info by mutableStateOf<StreamInfo?>(null)
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var aspect by mutableStateOf(9f / 16f)
        var playing by mutableStateOf(false)
        var job: Job? = null
        var counted = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) loading = false }
            override fun onPlayerError(e: PlaybackException) { error = "Couldn't play this clip (${e.errorCodeName})"; loading = false }
        }
    }

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val slots = androidx.compose.runtime.mutableStateMapOf<String, Slot>()
    private val spare = ArrayDeque<ExoPlayer>()

    var activeId by mutableStateOf<String?>(null)
        private set
    val active: Slot? get() = activeId?.let { slots[it] }
    val playing: Boolean get() = active?.playing == true

    fun init(context: Context) { app = context.applicationContext }

    fun slot(id: String): Slot? = slots[id]

    /** Starts fetching + buffering [v] without playing it. Safe to call repeatedly. */
    fun preload(v: VideoRef) {
        if (slots.containsKey(v.id)) return
        val player = spare.removeFirstOrNull() ?: newPlayer(app)
        val slot = Slot(v.id, player)
        player.addListener(slot.listener)
        player.repeatMode = if (Prefs.loopClips) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.volume = 1f
        player.playWhenReady = false
        slots[v.id] = slot
        slot.job = scope.launch {
            try {
                val i = Repo.stream(v.url)
                slot.info = i
                // 720p is plenty for a phone-sized clip and starts fast.
                val (src, _) = Sources.build(i, 720)
                player.setMediaSource(src)
                player.prepare()
                if (activeId == v.id) player.play()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                slot.error = "Couldn't load this clip: ${e.message}"
                slot.loading = false
            }
        }
    }

    /** Shows [v]: plays its (already warm) player, pauses the rest, warms up [upcoming], drops the far ones. */
    fun activate(v: VideoRef, upcoming: List<VideoRef>, previous: VideoRef?) {
        Playback.player.pause()
        activeId = v.id
        preload(v)
        upcoming.forEach(::preload)
        previous?.let(::preload)
        val keep = (listOf(v.id) + upcoming.map { it.id } + listOfNotNull(previous?.id)).toSet()
        slots.values.toList().forEach { s ->
            when {
                s.id == v.id -> {
                    if (s.player.playbackState == Player.STATE_ENDED) s.player.seekTo(0)
                    s.player.play()
                    if (!s.counted) {
                        s.counted = true
                        scope.launch {
                            // Count it once it has really started.
                            val i = s.info ?: run { while (s.info == null && s.error == null) kotlinx.coroutines.delay(200); s.info } ?: return@launch
                            VideoRef.from(i).copy(short = true).let { Library.addHistory(it); com.bitdroid.tube.data.Sync.watched(it) }
                        }
                    }
                }
                s.id in keep -> { s.player.pause(); if (s.player.currentPosition > 0) s.player.seekTo(0) }
                else -> release(s)
            }
        }
    }

    private fun release(s: Slot) {
        s.job?.cancel()
        slots.remove(s.id)
        s.player.removeListener(s.listener)
        s.player.stop()
        s.player.clearMediaItems()
        if (spare.size < 2) spare.addLast(s.player) else s.player.release()
    }

    fun toggle() {
        val p = active?.player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { slots.values.forEach { it.player.pause() } }

    /** Called when leaving Clips for good (e.g. a fresh batch): frees every player. */
    fun clear() {
        slots.values.toList().forEach(::release)
        activeId = null
    }
}
