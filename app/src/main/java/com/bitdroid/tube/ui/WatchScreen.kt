package com.bitdroid.tube.ui

import android.view.HapticFeedbackConstants
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.bitdroid.tube.data.ChannelRef
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Prefs
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.clock
import com.bitdroid.tube.data.compact
import com.bitdroid.tube.player.Playback
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** The video picture. A TextureView so it clips to Bloom's rounded corners. */
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        update = { player.setVideoTextureView(it) },
        onRelease = { player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

/** Live position of a player, polled a few times a second. */
class Progress {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
}

@Composable
fun rememberProgress(player: Player): Progress {
    val p = remember { Progress() }
    LaunchedEffect(player) {
        while (true) {
            p.position = player.currentPosition
            p.duration = player.duration.coerceAtLeast(0)
            p.buffered = player.bufferedPosition
            delay(200)
        }
    }
    return p
}

/**
 * Material You's squiggly progress line: the played part waves while the video plays and flattens
 * when it's paused. Tap or drag anywhere to seek.
 */
@Composable
fun WavyProgress(progress: Progress, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, color: Color = Bit.accent) {
    val amp by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f), label = "amp")
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase")
    var drag by remember { mutableFloatStateOf(-1f) }
    val dur = progress.duration
    val frac = if (drag >= 0) drag else if (dur > 0) progress.position.toFloat() / dur else 0f
    val buf = if (dur > 0) progress.buffered.toFloat() / dur else 0f
    Canvas(
        modifier.fillMaxWidth().height(30.dp)
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { if (dur > 0 && drag >= 0) onSeek((drag * dur).toLong()); drag = -1f },
                    onDragCancel = { drag = -1f },
                ) { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val cy = size.height / 2
        val stroke = 4.dp.toPx()
        val x = (size.width * frac.coerceIn(0f, 1f))
        // rest of the track
        drawLine(Color.White.copy(alpha = 0.22f), Offset(x, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.38f), Offset(x, cy), Offset(size.width * buf.coerceIn(0f, 1f), cy), stroke, StrokeCap.Round)
        // the squiggle
        val a = 3.2.dp.toPx() * amp * (if (drag >= 0) 0.4f else 1f)
        val wl = 22.dp.toPx()
        val path = Path()
        var px = 0f
        path.moveTo(0f, cy)
        while (px < x) {
            path.lineTo(px, cy + a * sin(px / wl * 2 * PI.toFloat() - phase))
            px += 2f
        }
        path.lineTo(x, cy + a * sin(x / wl * 2 * PI.toFloat() - phase))
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        // thumb: a soft vertical pill
        val tw = 5.dp.toPx()
        val th = if (drag >= 0) 24.dp.toPx() else 18.dp.toPx()
        drawRoundRect(color, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
    }
}

/** Play/pause that morphs between a clover (paused) and a squircle (playing). */
@Composable
fun MorphPlayButton(playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, bg: Color = Bit.accent, fg: Color = Bit.onAccent) {
    val m by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "morph")
    Box(
        Modifier.size(size).squish(onClick = onClick).graphicsLayer { rotationZ = m * 45f }
            .clip(Shapes.morph(Shapes.clover, Shapes.squircle, m)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = fg,
            modifier = Modifier.size(size * 0.46f).graphicsLayer { rotationZ = -m * 45f },
        )
    }
}

// ================================================================== the player box

@Composable
private fun PlayerBox(modifier: Modifier, fullscreen: Boolean) {
    val view = LocalView.current
    val player = Playback.player
    val progress = rememberProgress(player)
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableStateOf(0) }
    var seekFlash by remember { mutableStateOf(0) } // -1 back, +1 forward
    LaunchedEffect(controls, Playback.playing, poke) {
        if (controls && Playback.playing) { delay(3200); controls = false }
    }
    LaunchedEffect(seekFlash) { if (seekFlash != 0) { delay(550); seekFlash = 0 } }
    val step = Prefs.seekStep

    BoxWithConstraints(modifier.background(Color.Black)) {
        val w = constraints.maxWidth
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(Playback.aspect.coerceIn(0.4f, 3f), matchHeightConstraintsFirst = Playback.aspect < 1.5f || fullscreen)) {
                VideoSurface(player, Modifier.fillMaxSize())
            }
        }
        // Thumbnail until the first frame.
        if (Playback.loading || (progress.position == 0L && !Playback.playing && Playback.error == null)) {
            Img(Playback.video?.thumb, Modifier.fillMaxSize(), scale = androidx.compose.ui.layout.ContentScale.Fit)
        }
        Box(
            Modifier.fillMaxSize().pointerInput(w) {
                detectTapGestures(
                    onTap = { controls = !controls; poke++ },
                    onDoubleTap = { o ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (o.x < w / 2) { Playback.seekBy(-step); seekFlash = -1 } else { Playback.seekBy(step); seekFlash = 1 }
                        poke++
                    },
                )
            }.pointerInput(fullscreen) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > 140) { if (fullscreen) Playback.fullscreen = false else Playback.expanded = false }
                        else if (total < -140 && !fullscreen) Playback.fullscreen = true
                    },
                ) { _, dy -> total += dy }
            },
        )
        // Double-tap seek bubbles.
        AnimatedVisibility(seekFlash != 0, Modifier.align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 36.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(84.dp).clip(Shapes.Ripple).background(Bit.soft.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Text(if (seekFlash < 0) "−$step s" else "+$step s", style = mono(14f, Bit.onSoft))
            }
        }
        if (Playback.loading || (Playback.buffering && !controls)) {
            CookieLoader(Modifier.align(Alignment.Center), 56.dp)
        }
        Playback.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, style = body(14.5f, Color.White, FontWeight.Medium))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Retry", { Playback.retry() })
                    if (Playback.qualities.isNotEmpty()) PillButton("Quality", { Sheets.quality = true }, filled = false)
                }
            }
        }
        AnimatedVisibility(controls && Playback.error == null && !Playback.loading, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.65f))))) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).then(if (fullscreen) Modifier.statusBarsPadding() else Modifier).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CookieButton(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.KeyboardArrowDown, if (fullscreen) "Exit full screen" else "Minimize",
                        { if (fullscreen) Playback.fullscreen = false else Playback.expanded = false },
                        size = 40.dp, shape = Shapes.Circle, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White,
                    )
                    Text(
                        if (fullscreen) Playback.video?.title.orEmpty() else "", style = body(15f, Color.White, FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    if (Playback.playingHeight > 0) {
                        Text("${Playback.playingHeight}p", style = mono(11f, Bit.onSoft), modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Bit.soft).padding(horizontal = 8.dp, vertical = 4.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    CookieButton(Icons.Rounded.Tune, "Quality and speed", { Sheets.quality = true }, size = 40.dp, shape = Shapes.Sunny, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                }
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    CookieButton(Icons.Rounded.Replay10, "Back $step seconds", { Playback.seekBy(-step); poke++ }, size = 50.dp, shape = Shapes.Flower, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                    MorphPlayButton(Playback.playing, 70.dp, { Playback.toggle(); poke++ })
                    CookieButton(Icons.Rounded.Forward10, "Forward $step seconds", { Playback.seekBy(step); poke++ }, size = 50.dp, shape = Shapes.Flower, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                }
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(start = 16.dp, end = 10.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val live = Playback.video?.live == true
                        Text(
                            if (live) "LIVE" else "${clock(progress.position / 1000)} / ${clock(progress.duration / 1000)}",
                            style = mono(12f, Color.White), modifier = Modifier.weight(1f),
                        )
                        CookieButton(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Full screen",
                            { Playback.fullscreen = !Playback.fullscreen }, size = 38.dp, shape = Shapes.Circle, bg = Color.Transparent, tint = Color.White,
                        )
                    }
                    WavyProgress(progress, Playback.playing, { player.seekTo(it); poke++ })
                }
            }
        }
    }
}

// ================================================================== full watch page

@Composable
private fun ActionPill(icon: ImageVector, label: String?, on: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.squish { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onClick() }
            .clip(RoundedCornerShape(20.dp)).background(if (on) Bit.accent else Bit.high)
            .padding(start = 8.dp, end = if (label != null) 16.dp else 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(Shapes.Sunny).background(if (on) Bit.onAccent.copy(alpha = 0.16f) else Bit.raised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (on) Bit.onAccent else Bit.accent, modifier = Modifier.size(18.dp))
        }
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = body(14f, if (on) Bit.onAccent else Bit.text, FontWeight.SemiBold))
        }
    }
}

@Composable
fun WatchScreen() {
    val context = LocalContext.current
    val v = Playback.video ?: return
    val info = Playback.info
    BackHandler { if (Playback.fullscreen) Playback.fullscreen = false else Playback.expanded = false }

    if (Playback.fullscreen) {
        PlayerBox(Modifier.fillMaxSize(), fullscreen = true)
        return
    }
    Column(Modifier.fillMaxSize().background(Bit.bg)) {
        PlayerBox(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp).clip(RoundedCornerShape(28.dp)).aspectRatio(16f / 9f), fullscreen = false)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(v.title, style = body(18.5f, Bit.text, FontWeight.Bold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val stats = listOfNotNull(
                        if (v.views >= 0) "${compact(v.views)} views" else null,
                        v.age ?: info?.textualUploadDate,
                    ).joinToString("  ·  ")
                    if (stats.isNotEmpty()) Text(stats.uppercase(), style = mono(11.5f))
                }
            }
            item {
                val liked = Library.isLiked(v)
                val disliked = Library.isDisliked(v)
                val likes = info?.likeCount?.takeIf { it >= 0 } ?: Playback.votes?.first?.takeIf { it >= 0 }
                val dislikes = Playback.votes?.second?.takeIf { it >= 0 && Prefs.showDislikes }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill(if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, likes?.let { compact(it + if (liked) 1 else 0) } ?: "Like", liked) { com.bitdroid.tube.data.Sync.like(v) }
                    ActionPill(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, dislikes?.let { compact(it + if (disliked) 1 else 0) } ?: "Dislike", disliked) { com.bitdroid.tube.data.Sync.dislike(v) }
                    ActionPill(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save") { Sheets.save = v }
                    val later = Library.inPlaylist(Library.WATCH_LATER, v)
                    ActionPill(Icons.Rounded.Schedule, if (later) "Saved for later" else "Later", later) { com.bitdroid.tube.data.Sync.watchLater(v) }
                    ActionPill(Icons.Rounded.Share, "Share") { share(context, v) }
                    ActionPill(Icons.Rounded.Tune, "Quality") { Sheets.quality = true }
                }
            }
            item {
                val ref = ChannelRef(v.channelUrl ?: "", v.channel, v.avatar, info?.uploaderSubscriberCount ?: -1)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp)).background(Bit.surface)
                        .squish(scale = 0.98f) { if (v.channelUrl != null) { Playback.expanded = false; Nav.go(Screen.Channel(v.channelUrl, v.channel)) } }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(v.avatar, v.channelUrl ?: v.channel, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.channel, style = body(15.5f, Bit.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ref.subscribers >= 0) Text("${compact(ref.subscribers)} subscribers", style = body(12.5f, Bit.dim))
                    }
                    if (v.channelUrl != null) SubscribeButton(ref)
                }
            }
            val desc = info?.description?.content?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim() }
            if (!desc.isNullOrBlank()) {
                item {
                    var open by remember(v.url) { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp)).background(Bit.surface)
                            .squish(scale = 0.99f) { open = !open }.padding(16.dp),
                    ) {
                        Text("ABOUT", style = mono(11f, Bit.accent))
                        Spacer(Modifier.height(6.dp))
                        Text(desc, style = body(14f, Bit.text), maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                        if (!open) Text("more", style = body(13.5f, Bit.accent, FontWeight.Bold))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Up next", Modifier.weight(1f))
                    Chip(if (Prefs.autoplay) "Autoplay on" else "Autoplay off", Prefs.autoplay, { Prefs.setAutoplay(!Prefs.autoplay) }, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (info == null && Playback.loading) item { CenterLoader() }
            items(Playback.related.filter { !it.short }, key = { "n" + it.url }) { r ->
                VideoRow(r, { Playback.open(r) }, { Sheets.open(r) })
            }
        }
    }
}

// ================================================================== mini player

@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val v: VideoRef = Playback.video ?: return
    val progress = rememberProgress(Playback.player)
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp).widthIn(max = 600.dp)
            .graphicsLayer { translationY = drag.coerceAtLeast(0f); alpha = 1f - (drag / 400f).coerceIn(0f, 0.7f) }
            .clip(RoundedCornerShape(26.dp)).background(Bit.high)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > 120) Playback.close() else if (drag < -60) Playback.expanded = true; drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag += dy }
            }
            .squish(scale = 0.97f) { Playback.expanded = true },
    ) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(19.dp)).background(Color.Black)) {
                VideoSurface(Playback.player, Modifier.fillMaxSize())
                if (Playback.loading) CookieLoader(Modifier.align(Alignment.Center), 26.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = body(14f, Bit.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.channel, style = body(12.5f, Bit.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MorphPlayButton(Playback.playing, 44.dp, { Playback.toggle() })
            Spacer(Modifier.width(6.dp))
            CookieButton(Icons.Rounded.Close, "Close", { Playback.close() }, size = 40.dp, shape = Shapes.Circle, bg = Color.Transparent, tint = Bit.dim)
        }
        val f = if (progress.duration > 0) progress.position.toFloat() / progress.duration else 0f
        Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Bit.raised)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).clip(CircleShape).background(Bit.accent))
        }
    }
}
