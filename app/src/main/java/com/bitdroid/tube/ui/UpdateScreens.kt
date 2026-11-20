package com.bitdroid.tube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.tube.update.Updater
import kotlinx.coroutines.launch

/** Settings → Updates: installed version, check, download + install with progress. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val installed = "v${Updater.installedName(context)} · build ${Updater.installedCode(context)}"
    val status = when (val s = state) {
        Updater.State.Idle -> "Updates come from bitTube's GitHub releases."
        Updater.State.Checking -> "Checking…"
        Updater.State.UpToDate -> "You're up to date."
        is Updater.State.Available -> "Update ready: v${s.info.versionName}"
        is Updater.State.Downloading -> "Downloading… ${(s.progress * 100).toInt()}%"
        Updater.State.Installing -> "Installing…"
        is Updater.State.Error -> s.message
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("App updates", style = body(16f, Bit.text, FontWeight.Medium))
        Text(installed, style = mono(12f))
        Spacer(Modifier.height(6.dp))
        Text(status, style = body(14f, if (state is Updater.State.Error) Bit.error else Bit.dim))
        val s = state
        if (s is Updater.State.Downloading) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Bit.raised)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(Bit.accent))
            }
        }
        Spacer(Modifier.height(12.dp))
        when (s) {
            is Updater.State.Available -> PillButton("Install update", { scope.launch { Updater.install(context, s.info) } }, icon = Icons.Rounded.Download)
            is Updater.State.Error -> if (s.info != null) PillButton("Install update", { scope.launch { Updater.install(context, s.info) } }, icon = Icons.Rounded.Download)
            else PillButton("Check for updates", { scope.launch { Updater.check(context) } })
            is Updater.State.Downloading, Updater.State.Installing, Updater.State.Checking -> PillButton("Please wait…", {}, filled = false)
            else -> PillButton("Check for updates", { scope.launch { Updater.check(context) } })
        }
    }
}

/**
 * Full-screen update page. Shows every time bitTube opens and a newer release is on GitHub.
 * "Later" hides it until the next launch.
 */
@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val info = when (val s = state) {
        is Updater.State.Available -> s.info
        is Updater.State.Downloading -> s.info
        is Updater.State.Error -> s.info
        else -> null
    }
    val busy = state is Updater.State.Downloading || state == Updater.State.Installing
    if (info == null && !busy) return
    BackHandler(enabled = !busy) { Updater.dismissed = true }

    val t = rememberInfiniteTransition(label = "update")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    Box(Modifier.fillMaxSize().backdrop().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(210.dp).graphicsLayer { rotationZ = spin; scaleX = breathe; scaleY = breathe }.clip(Shapes.Ripple).background(Bit.soft))
                Box(Modifier.size(132.dp).graphicsLayer { rotationZ = -spin * 1.5f }.clip(Shapes.Clover).background(Bit.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = Bit.onAccent, modifier = Modifier.size(54.dp).graphicsLayer { rotationZ = spin * 1.5f })
                }
            }
            Spacer(Modifier.height(34.dp))
            Text("A fresh bitTube\nis ready", style = display(28f, weight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "v${info?.versionName ?: "…"}  ·  you have v${Updater.installedName(context)}",
                style = mono(13f, Bit.accent),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Update now to get the newest fixes. It downloads from GitHub and installs right here.",
                style = body(15f, Bit.dim), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                is Updater.State.Downloading -> {
                    val p by animateFloatAsState(s.progress, label = "p")
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Bit.raised)) {
                        Box(Modifier.fillMaxWidth(p.coerceIn(0.03f, 1f)).height(14.dp).clip(CircleShape).background(Bit.accent))
                    }
                    Text("Downloading… ${(s.progress * 100).toInt()}%", style = mono(13f))
                }
                Updater.State.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CookieLoader(size = 28.dp)
                    Text("  Installing…", style = body(15f, Bit.text, FontWeight.SemiBold))
                }
                else -> {
                    if (s is Updater.State.Error) Text(s.message, style = body(14f, Bit.error), textAlign = TextAlign.Center)
                    info?.let { i ->
                        Box(
                            Modifier.fillMaxWidth().height(62.dp).squish(scale = 0.96f) { scope.launch { Updater.install(context, i) } }
                                .clip(CircleShape).background(Bit.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Download, null, tint = Bit.onAccent)
                                Text("  Update now", style = body(17f, Bit.onAccent, FontWeight.Bold))
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).squish(scale = 0.96f) { Updater.dismissed = true }.clip(CircleShape).background(Bit.high),
                        contentAlignment = Alignment.Center,
                    ) { Text("Later", style = body(16f, Bit.text, FontWeight.SemiBold)) }
                }
            }
        }
    }
}
