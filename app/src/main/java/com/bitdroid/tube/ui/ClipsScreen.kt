package com.bitdroid.tube.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.ChannelRef
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.data.Sync
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.compact
import com.bitdroid.tube.player.ClipPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** The clip list survives tab switches, and gets warmed up at launch so the tab opens instantly. */
object ClipFeed {
    val items = mutableStateListOf<VideoRef>()
    /** Which sign-in session these clips were loaded under; clips from before you signed in get replaced. */
    private var loadedSession = -1
    val stale: Boolean get() = items.isNotEmpty() && loadedSession != com.bitdroid.tube.data.Account.session
    var page = 0
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun more() {
        if (loading) return
        loading = true
        error = null
        try {
            if (items.isEmpty()) loadedSession = com.bitdroid.tube.data.Account.session
            val seen = items.map { it.id }.toHashSet()
            items.addAll(Repo.clips().filter { it.id !in seen })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't load clips: ${e.message}"
        } finally {
            loading = false
        }
    }

    /** Fetch the first batch and buffer the first two clips in the background (paused). */
    suspend fun warmUp() {
        if (!com.bitdroid.tube.data.Account.signedIn) return
        if (stale) fresh() else if (items.isEmpty()) more()
        items.getOrNull(page)?.let(ClipPlayback::preload)
        items.getOrNull(page + 1)?.let(ClipPlayback::preload)
    }

    suspend fun fresh() {
        ClipPlayback.clear()
        items.clear()
        page = 0
        Repo.resetClips()
        more()
    }
}

@Composable
fun ClipsScreen() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(com.bitdroid.tube.data.Account.session) { if (ClipFeed.stale) ClipFeed.fresh() else if (ClipFeed.items.isEmpty()) ClipFeed.more() }
    DisposableEffect(Unit) { onDispose { ClipPlayback.pause() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val items = ClipFeed.items
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().backdrop(), contentAlignment = Alignment.Center) {
                when {
                    ClipFeed.loading -> CookieLoader(size = 64.dp)
                    ClipFeed.error != null -> ErrorState(ClipFeed.error!!) { scope.launch { ClipFeed.more() } }
                    else -> MessageState(Icons.Rounded.VideoLibrary, "No clips right now", null, "Try again", { scope.launch { ClipFeed.more() } })
                }
            }
        } else {
            val pager = rememberPagerState(initialPage = ClipFeed.page.coerceIn(0, items.lastIndex)) { items.size }
            // As soon as a swipe passes halfway, start warming the clips after it.
            LaunchedEffect(pager) {
                snapshotFlow { pager.currentPage }.collect { p ->
                    items.getOrNull(p)?.let(ClipPlayback::preload)
                    items.getOrNull(p + 1)?.let(ClipPlayback::preload)
                }
            }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { p ->
                    ClipFeed.page = p
                    val v = items.getOrNull(p) ?: return@collect
                    ClipPlayback.activate(v, listOfNotNull(items.getOrNull(p + 1), items.getOrNull(p + 2)), items.getOrNull(p - 1))
                    if (p >= items.size - 5) ClipFeed.more()
                }
            }
            VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items[it].id }) { page ->
                ClipPage(items[page], active = page == pager.settledPage)
            }
        }
        Row(Modifier.statusBarsPadding().padding(start = 22.dp, top = 14.dp, end = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Clips", style = display(26f, Color.White, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
            CookieButton(Icons.Rounded.Refresh, "Fresh clips", { scope.launch { ClipFeed.fresh() } }, shape = Shapes.Flower, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White)
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String?, on: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).squish { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onClick() }
                .clip(Shapes.Sunny).background(if (on) Bit.accent else Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Bit.onAccent else Color.White, modifier = Modifier.size(25.dp)) }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = mono(11f, Color.White))
        }
    }
}

// ================================================================== double-tap hearts

private class Heart(val id: Long, val at: Offset, val tilt: Float)

/**
 * One heart: pops in big with a bounce, tilted a little, then floats up and fades.
 * A ring of small cookie sparks bursts out behind it (the Bloom touch).
 */
@Composable
private fun HeartBurst(h: Heart, onDone: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val spark = remember { Animatable(0f) }
    LaunchedEffect(h.id) {
        launch { spark.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        scale.animateTo(1.25f, spring(dampingRatio = 0.42f, stiffness = 520f))
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
        launch { rise.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        fade.animateTo(0f, tween(520, delayMillis = 60))
        onDone()
    }
    val density = LocalDensity.current
    val size = 110.dp
    val half = with(density) { (size / 2).toPx() }
    Box(
        Modifier.offset { IntOffset((h.at.x - half).roundToInt(), (h.at.y - half - rise.value * 160.dp.toPx()).roundToInt()) }
            .size(size)
            .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center,
    ) {
        // sparks
        val spread = with(density) { 70.dp.toPx() }
        repeat(8) { i ->
            val a = Math.toRadians(i * 45.0 + h.tilt)
            Box(
                Modifier.offset {
                    val d = spark.value * spread
                    IntOffset((kotlin.math.cos(a) * d).roundToInt(), (kotlin.math.sin(a) * d).roundToInt())
                }
                    .size(12.dp)
                    .graphicsLayer { val s = (1f - spark.value).coerceAtLeast(0f); scaleX = s; scaleY = s }
                    .clip(if (i % 2 == 0) Shapes.Sunny else Shapes.Clover)
                    .background(if (i % 2 == 0) Bit.accent else Bit.onPink),
            )
        }
        Icon(
            Icons.Rounded.Favorite, null,
            tint = Bit.accent,
            modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = h.tilt },
        )
    }
}

// ================================================================== one clip

@Composable
private fun ClipPage(v: VideoRef, active: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp
    var showPlay by remember { mutableStateOf(false) }
    val slot = ClipPlayback.slot(v.id)
    val info = slot?.info
    // Fill in details the list didn't have once the clip loads.
    val details = info?.let { i -> VideoRef.from(i).let { it.copy(short = true, avatar = it.avatar ?: v.avatar) } } ?: v
    val hearts = remember { mutableStateListOf<Heart>() }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 66.dp, bottom = bottom)
            .clip(RoundedCornerShape(34.dp)).background(Bit.surface)
            .pointerInput(v.id, active, details) {
                detectTapGestures(
                    onTap = { if (active) { ClipPlayback.toggle(); showPlay = slot?.player?.playWhenReady == false } },
                    onDoubleTap = { o ->
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        hearts += Heart(System.nanoTime(), o, Random.nextInt(-22, 23).toFloat())
                        // Double-tap only ever likes (never un-likes), like the big apps.
                        if (!Library.isLiked(details)) Sync.like(details)
                    },
                )
            },
    ) {
        // Soft blurred cover behind, the clip on top.
        Img(v.thumb, Modifier.fillMaxSize().blur(36.dp))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        if (slot != null) {
            // Every nearby clip has its own warm player, so the next one is already showing its first frame.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(slot.aspect.coerceIn(0.4f, 2f), matchHeightConstraintsFirst = slot.aspect < 1f)) {
                    key(slot.player) { VideoSurface(slot.player, Modifier.fillMaxSize()) }
                }
            }
            if (active && slot.loading) CookieLoader(Modifier.align(Alignment.Center), 58.dp)
            slot.error?.let { Text(it, style = body(14f, Color.White), modifier = Modifier.align(Alignment.Center).padding(24.dp)) }
        }
        AnimatedVisibility(active && showPlay && slot?.playing == false, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            MorphPlayButton(false, 80.dp, { ClipPlayback.toggle(); showPlay = false })
        }
        Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))

        // Action rail
        val liked = Library.isLiked(details)
        val disliked = Library.isDisliked(details)
        val likes = info?.likeCount?.takeIf { it >= 0 }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, likes?.let { compact(it + if (liked) 1 else 0) }, liked) { Sync.like(details) }
            RailButton(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, null, disliked) { Sync.dislike(details) }
            RailButton(Icons.AutoMirrored.Rounded.PlaylistAdd, null, false) { Sheets.save = details }
            RailButton(Icons.Rounded.Share, null, false) { share(context, details) }
        }
        // Creator + title
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.squish { details.channelUrl?.let { Nav.go(Screen.Channel(it, details.channel)) } }) {
                    Avatar(details.avatar, details.channelUrl ?: details.channel, 38.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(details.channel, style = body(15f, Color.White, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (details.channelUrl != null) {
                    Spacer(Modifier.width(10.dp))
                    SubscribeButton(ChannelRef(details.channelUrl, details.channel, details.avatar))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(details.title, style = body(14.5f, Color.White, FontWeight.Medium), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (active && slot != null) {
                Spacer(Modifier.height(10.dp))
                WavyProgress(rememberProgress(slot.player), slot.playing, { slot.player.seekTo(it) }, color = Color.White)
            }
        }

        hearts.toList().forEach { h -> key(h.id) { HeartBurst(h) { hearts.remove(h) } } }
    }
}
