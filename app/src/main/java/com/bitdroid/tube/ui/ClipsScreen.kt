package com.bitdroid.tube.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.ChannelRef
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.compact
import com.bitdroid.tube.player.ClipPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Clips survive tab switches so you come back to where you were. */
private object ClipFeed {
    val items = mutableStateListOf<VideoRef>()
    var page = 0
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    suspend fun more() {
        if (loading) return
        loading = true
        error = null
        try {
            val seen = items.map { it.id }.toHashSet()
            items.addAll(Repo.clips().filter { it.id !in seen })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't load clips."
        } finally {
            loading = false
        }
    }
}

@Composable
fun ClipsScreen() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (ClipFeed.items.isEmpty()) ClipFeed.more() }
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
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { p ->
                    ClipFeed.page = p
                    items.getOrNull(p)?.let { ClipPlayback.play(it) }
                    if (p >= items.size - 4) ClipFeed.more()
                }
            }
            VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { items[it].id }) { page ->
                ClipPage(items[page], active = page == pager.settledPage)
            }
        }
        Row(Modifier.statusBarsPadding().padding(start = 22.dp, top = 14.dp, end = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Clips", style = display(26f, Color.White, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
            CookieButton(Icons.Rounded.Refresh, "Fresh clips", {
                scope.launch { ClipFeed.items.clear(); ClipFeed.page = 0; ClipFeed.more() }
            }, shape = Shapes.Flower, bg = Color.Black.copy(alpha = 0.35f), tint = Color.White)
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

@Composable
private fun ClipPage(v: VideoRef, active: Boolean) {
    val context = LocalContext.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp
    var showPlay by remember { mutableStateOf(false) }
    val clip = if (active) ClipPlayback.info else null
    // Fill in details the search result didn't have once the clip loads.
    val details = clip?.let { VideoRef.from(it).copy(short = true, avatar = v.avatar ?: VideoRef.from(it).avatar) } ?: v

    Box(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 66.dp, bottom = bottom)
            .clip(RoundedCornerShape(34.dp)).background(Bit.surface)
            .pointerInput(active) {
                detectTapGestures(
                    onTap = { if (active) { ClipPlayback.toggle(); showPlay = !ClipPlayback.player.playWhenReady } },
                    onDoubleTap = { if (!Library.isLiked(details)) Library.toggleLike(details) },
                )
            },
    ) {
        // Soft blurred cover behind, the clip itself on top.
        Img(v.thumb, Modifier.fillMaxSize().blur(36.dp))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        if (active) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(ClipPlayback.aspect.coerceIn(0.4f, 2f), matchHeightConstraintsFirst = ClipPlayback.aspect < 1f)) {
                    VideoSurface(ClipPlayback.player, Modifier.fillMaxSize())
                }
            }
            if (ClipPlayback.loading) CookieLoader(Modifier.align(Alignment.Center), 58.dp)
            ClipPlayback.error?.let { Text(it, style = body(14f, Color.White), modifier = Modifier.align(Alignment.Center)) }
        } else {
            Img(v.thumb, Modifier.fillMaxSize(), scale = androidx.compose.ui.layout.ContentScale.Fit)
        }
        AnimatedVisibility(active && showPlay && !ClipPlayback.playing, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            MorphPlayButton(false, 80.dp, { ClipPlayback.toggle(); showPlay = false })
        }
        Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))

        // Action rail
        val liked = Library.isLiked(details)
        val disliked = Library.isDisliked(details)
        val likes = clip?.likeCount?.takeIf { it >= 0 }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RailButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, likes?.let { compact(it + if (liked) 1 else 0) }, liked) { Library.toggleLike(details) }
            RailButton(if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, null, disliked) { Library.toggleDislike(details) }
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
            if (active) {
                Spacer(Modifier.height(10.dp))
                WavyProgress(rememberProgress(ClipPlayback.player), ClipPlayback.playing, { ClipPlayback.player.seekTo(it) }, color = Color.White)
            }
        }
    }
}
