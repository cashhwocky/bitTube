package com.bitdroid.tube.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

class TabItem(val icon: ImageVector, val label: String)

/**
 * The bitMessages floating tab bar, in Material You colors. A tonal pill marks the tab you're on and
 * that tab grows to show its name; drag along the bar and the pill follows your finger, its edge
 * rippling like a Bloom cookie, then springs onto the nearest tab. The clover on the right searches.
 */
@Composable
fun FloatingTabBar(
    haze: HazeState,
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchContent: @Composable () -> Unit = {},
) {
    val view = LocalView.current
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val barShape = RoundedCornerShape(32.dp)
    val tonal = Modifier.shadow(18.dp, barShape, ambientColor = Bit.bg, spotColor = Bit.bg).clip(barShape).hazeEffect(haze) {
        blurRadius = 26.dp
        tints = listOf(HazeTint(Bit.high.copy(alpha = 0.86f)))
        noiseFactor = 0f
    }
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(searching, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { s ->
            if (s) {
                Box(Modifier.fillMaxWidth().height(68.dp).then(tonal).padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) { searchContent() }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp).then(tonal).padding(7.dp)) {
                    val density = LocalDensity.current
                    val n = tabs.size
                    val big = 1.8f // the selected tab is this many "units" wide
                    val unit = with(density) { maxWidth.toPx() } / (n - 1 + big)
                    val x = remember { Animatable(selected * unit) }
                    var dragging by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val settle = spring<Float>(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow)
                    LaunchedEffect(selected, unit) { if (!dragging) x.animateTo(selected * unit, settle) }
                    val wave by animateFloatAsState(if (dragging) 0.07f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "wave")
                    val spin = rememberInfiniteTransition(label = "ripple")
                    val phase by spin.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
                    val hover = (x.value / unit).roundToInt().coerceIn(0, n - 1)
                    val on = if (dragging) hover else selected
                    Box(
                        Modifier.offset { IntOffset(x.value.roundToInt(), 0) }
                            .width(with(density) { (unit * big).toDp() }).fillMaxHeight()
                            .graphicsLayer { val s2 = 1f + wave * 0.8f; scaleX = s2; scaleY = s2 }
                            .clip(WavyPill(wave, phase))
                            .background(Bit.soft),
                    )
                    Row(
                        Modifier.fillMaxSize().pointerInput(n, unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    val target = (x.value / unit).roundToInt().coerceIn(0, n - 1)
                                    dragging = false
                                    scope.launch { x.animateTo(target * unit, settle) }
                                    if (target != currentSelected) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        currentOnSelect(target)
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch { x.animateTo(currentSelected * unit, settle) }
                                },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { x.snapTo((x.value + dx).coerceIn(0f, unit * (n - 1))) }
                            }
                        },
                    ) {
                        tabs.forEachIndexed { i, t ->
                            val active = i == on
                            val w by animateFloatAsState(if (active) big else 1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "w$i")
                            Row(
                                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(26.dp)).squish {
                                    if (i != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelect(i)
                                },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(t.icon, t.label, tint = if (active) Bit.onSoft else Bit.dim, modifier = Modifier.size(23.dp))
                                AnimatedVisibility(active, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                                    Row {
                                        Spacer(Modifier.width(7.dp))
                                        Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Bit.onSoft, maxLines = 1, overflow = TextOverflow.Clip)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(68.dp).squish(onClick = onSearch).clip(Shapes.Clover).background(Bit.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search", tint = Bit.onAccent, modifier = Modifier.size(26.dp)) }
    }
}
