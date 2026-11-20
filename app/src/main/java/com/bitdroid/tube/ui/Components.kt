package com.bitdroid.tube.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bitdroid.tube.data.ChannelRef
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.clock
import com.bitdroid.tube.data.compact
import com.bitdroid.tube.data.meta

// ---------------------------------------------------------------- images

@Composable
fun Img(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp), scale: ContentScale = ContentScale.Crop) {
    Box(modifier.clip(shape).background(Bit.raised)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize())
    }
}

/** Creator avatar in that creator's own cookie shape. */
@Composable
fun Avatar(url: String?, key: String, size: Dp, modifier: Modifier = Modifier) {
    Img(url, modifier.size(size), Shapes.forKey(key))
}

// ---------------------------------------------------------------- loading / empty / error

/** Bloom loading indicator: a cookie that spins and morphs between shapes. */
@Composable
fun CookieLoader(modifier: Modifier = Modifier, size: Dp = 52.dp, color: Color = Bit.accent) {
    val t = rememberInfiniteTransition(label = "loader")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin")
    val m by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "morph")
    Box(modifier.size(size).clip(Shapes.morph(Shapes.ripple, Shapes.flower, m, Math.toRadians(spin.toDouble()))).background(color))
}

@Composable
fun CenterLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { CookieLoader() }
}

@Composable
fun MessageState(icon: ImageVector, title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(Shapes.Sunny).background(Bit.soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Bit.onSoft, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = display(17f), textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = body(14.5f, Bit.dim), textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PillButton(action, onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) =
    MessageState(Icons.Rounded.SensorsOff, "Hmm, that didn't load", message, "Try again", onRetry)

// ---------------------------------------------------------------- buttons, chips, headers

@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = true) {
    Row(
        modifier.squish(onClick = onClick).clip(CircleShape).background(if (filled) Bit.accent else Bit.raised).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Bit.onAccent else Bit.text, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = body(15f, if (filled) Bit.onAccent else Bit.text, FontWeight.Bold))
    }
}

/** A round-ish tonal button in a Bloom shape. */
@Composable
fun CookieButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 46.dp, shape: Shape = Shapes.Sunny, bg: Color = Bit.high, tint: Color = Bit.text) {
    Box(modifier.size(size).squish(onClick = onClick).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.squish(onClick = onClick).clip(RoundedCornerShape(14.dp)).background(if (selected) Bit.accent else Bit.high)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Bit.onAccent else Bit.dim, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = body(14f, if (selected) Bit.onAccent else Bit.text, FontWeight.SemiBold))
    }
}

/** Big Bloom page title with optional back arrow and actions. */
@Composable
fun Header(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack != null) 10.dp else 22.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CookieButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 44.dp, shape = Shapes.Circle)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = display(if (onBack != null) 21f else 27f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = body(13.5f, Bit.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = mono(11.5f, Bit.accent), modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp))
}

// ---------------------------------------------------------------- video tiles

@Composable
fun DurationBadge(v: VideoRef, modifier: Modifier = Modifier) {
    val label = when {
        v.live -> "LIVE"
        v.duration > 0 -> clock(v.duration)
        else -> return
    }
    Text(
        label,
        style = mono(11f, if (v.live) Bit.onPink else Bit.onSoft),
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (v.live) Bit.pink else Bit.soft.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Progress you made last time, as a thin accent line on the thumbnail. */
@Composable
private fun ResumeBar(v: VideoRef, modifier: Modifier = Modifier) {
    val pos = com.bitdroid.tube.data.Library.positions[v.id] ?: return
    if (v.duration <= 0) return
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.35f))) {
        Box(Modifier.fillMaxWidth((pos.toFloat() / v.duration).coerceIn(0.03f, 1f)).height(4.dp).background(Bit.accent))
    }
}

/**
 * The main video tile: thumbnail inside a tonal card, the creator's cookie avatar, title and details
 * underneath. Long-press (or the dots) opens the actions sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTile(v: VideoRef, onOpen: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).clip(RoundedCornerShape(30.dp)).background(Bit.surface)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen, onLongClick = onMore)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(23.dp))) {
            Img(v.thumb, Modifier.fillMaxSize())
            DurationBadge(v, Modifier.align(Alignment.BottomEnd).padding(10.dp))
            ResumeBar(v, Modifier.align(Alignment.BottomCenter))
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 0.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Avatar(v.avatar, v.channelUrl ?: v.channel, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = body(15.5f, Bit.text, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(v.meta(), style = body(13f, Bit.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.MoreVert, "More", tint = Bit.dim, modifier = Modifier.size(40.dp).clip(CircleShape).squish(onClick = onMore).padding(9.dp))
        }
    }
}

/** Compact row: small thumbnail on the left. For Up next, library lists and search. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(v: VideoRef, onOpen: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).clip(RoundedCornerShape(24.dp)).background(Bit.surface)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen, onLongClick = onMore)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))) {
            Img(v.thumb, Modifier.fillMaxSize())
            DurationBadge(v, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            ResumeBar(v, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = body(14.5f, Bit.text, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(v.meta(), style = body(12.5f, Bit.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.MoreVert, "More", tint = Bit.dim, modifier = Modifier.size(38.dp).clip(CircleShape).squish(onClick = onMore).padding(8.dp))
    }
}

@Composable
fun ChannelRow(c: ChannelRef, onOpen: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).clip(RoundedCornerShape(24.dp)).background(Bit.surface)
            .squish(scale = 0.97f, onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.avatar, c.url, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = body(16f, Bit.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.subscribers >= 0) Text("${compact(c.subscribers)} subscribers", style = body(13f, Bit.dim))
        }
        trailing()
    }
}

@Composable
fun RetryRow(onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
        PillButton("Load more", onRetry, icon = Icons.Rounded.Refresh, filled = false)
    }
}

/** Calls [onEnd] when the list is scrolled near its last item. */
@Composable
fun LoadMoreWhenNearEnd(state: LazyListState, onEnd: suspend () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.collect { near -> if (near) onEnd() }
    }
}
