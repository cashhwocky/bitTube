package com.bitdroid.tube.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.Account
import com.bitdroid.tube.data.AppIcons
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.AccountCircle
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Prefs

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(30.dp)).background(Bit.surface).padding(vertical = 8.dp), content = content)
}

/** Bloom switch: the knob grows into a cookie when it's on. */
@Composable
fun BloomSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "knob")
    Box(Modifier.width(54.dp).height(32.dp).clip(CircleShape).background(if (on) Bit.accent else Bit.raised).padding(4.dp)) {
        Box(
            Modifier.offset(x = x).size(24.dp).clip(if (on) Shapes.Sunny else CircleShape).background(if (on) Bit.onAccent else Bit.dim),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Icons.Rounded.Check, null, tint = Bit.accent, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().squish(scale = 0.98f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(!on) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(16f, Bit.text, FontWeight.Medium))
            if (sub != null) Text(sub, style = body(13f, Bit.dim))
        }
        Spacer(Modifier.width(12.dp))
        BloomSwitch(on)
    }
}

@Composable
private fun ActionRow(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().squish(scale = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = body(16f, Bit.text, FontWeight.Medium))
        if (sub != null) Text(sub, style = body(13f, Bit.dim))
    }
}

@Composable
private fun <T> ChoiceRow(title: String, sub: String?, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = body(16f, Bit.text, FontWeight.Medium), modifier = Modifier.padding(horizontal = 20.dp))
        if (sub != null) Text(sub, style = body(13f, Bit.dim), modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) -> Chip(label, v == value, { onPick(v) }) }
        }
    }
}

private val qualityOptions = listOf(0 to "Best", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", 240 to "240p")

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header("Settings", "bitTube", onBack = { Nav.back() }) }
        item {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var importing by remember { mutableStateOf<String?>(null) }
            Group("Account") {
                if (Account.signedIn) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(Shapes.Sunny).background(Bit.soft).padding(3.dp)) {
                            Img(Account.photo, Modifier.fillMaxSize(), Shapes.Sunny)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Account.name ?: "Signed in", style = body(17f, Bit.text, FontWeight.SemiBold))
                            Text(Account.handle ?: "YouTube account", style = body(13f, Bit.dim))
                        }
                        PillButton("Sign out", { Account.signOut() }, filled = false)
                    }
                    ToggleRow("Send watch history to YouTube", "So your For you page learns from what you watch here", Prefs.syncHistory) { Prefs.setSyncHistory(it) }
                    ActionRow("Import subscriptions", importing ?: "Copy everyone you follow on YouTube into bitTube") {
                        importing = "Importing…"
                        scope.launch {
                            importing = runCatching { Account.channels() }.fold(
                                { Library.importSubscriptions(it); "Imported ${it.size} channels" },
                                { "Failed: ${it.message}" },
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Sign in to YouTube", style = body(17f, Bit.text, FontWeight.SemiBold))
                        Text("Get your For you page, subscriptions feed, likes, Watch later and playlists. You sign in on Google's own page; bitTube only keeps the session on this phone.", style = body(13.5f, Bit.dim))
                        Spacer(Modifier.height(12.dp))
                        PillButton("Sign in", { openSignIn(context) }, icon = androidx.compose.material.icons.Icons.Rounded.AccountCircle)
                    }
                }
            }
        }
        item { Group("Look") { ThemePicker(); IconPicker() } }
        item {
            Group("Playback") {
                ChoiceRow("Quality on Wi-Fi", "Highest quality to start videos at", qualityOptions, Prefs.qualityWifi) { Prefs.setQualityWifi(it) }
                ChoiceRow("Quality on mobile data", "Saves data on metered connections", qualityOptions, Prefs.qualityData) { Prefs.setQualityData(it) }
                ChoiceRow("Speed", null, listOf(0.75f to "0.75×", 1f to "Normal", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×"), Prefs.speed) { Prefs.setSpeed(it) }
                ChoiceRow("Double-tap to skip", null, listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"), Prefs.seekStep) { Prefs.setSeekStep(it) }
                ToggleRow("Autoplay", "Play the next video when one ends", Prefs.autoplay) { Prefs.setAutoplay(it) }
            }
        }
        item {
            Group("Clips") {
                ToggleRow("Loop clips", "Replay a clip until you swipe", Prefs.loopClips) { Prefs.setLoopClips(it) }
            }
        }
        item {
            Group("Videos") {
                ToggleRow("Show dislike counts", "Estimated counts from Return YouTube Dislike", Prefs.showDislikes) { Prefs.setShowDislikes(it) }
            }
        }
        item {
            var cleared by remember { mutableStateOf<String?>(null) }
            Group("Privacy") {
                ToggleRow("Keep watch history", "Used for For you and Keep watching. Stays on this phone.", Prefs.keepHistory) { Prefs.setKeepHistory(it) }
                ActionRow("Clear watch history", cleared?.takeIf { it == "h" }?.let { "Cleared" } ?: "${Library.history.size} videos") { Library.clearHistory(); cleared = "h" }
                ActionRow("Clear search history", cleared?.takeIf { it == "s" }?.let { "Cleared" } ?: "${Library.searches.size} searches") { Library.clearSearches(); cleared = "s" }
            }
        }
        item { Group("Updates") { UpdateCard() } }
        item {
            Group("About") {
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
                ActionRow("bitTube $version", "Made by Alex Desrosier · for bitDroid") {}
                ActionRow("Powered by NewPipe Extractor", "Playback needs no account or API key. Signing in is optional.") {}
            }
        }
    }
}

// ================================================================== pickers

@Composable
fun ThemePicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var current by remember { mutableStateOf(Themes.current()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("Theme", style = body(16f, Bit.text, FontWeight.Medium), modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.all.forEach { t ->
                val on = t.key == current
                val p = remember(t.key) { Themes.palette(context, t.key) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(70.dp).squish {
                            if (!on) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); Themes.set(context, t.key); current = t.key }
                        }.clip(if (on) Shapes.Ripple else Shapes.Squircle).background(p.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Mini preview: accent cookie over the container color.
                        Box(Modifier.size(42.dp).clip(Shapes.Clover).background(p.soft), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(p.accent))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.label, style = mono(10.5f, if (on) Bit.accent else Bit.dim))
                }
            }
        }
    }
}

@Composable
fun IconPicker() {
    val context = LocalContext.current
    val view = LocalView.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("App icon", style = body(16f, Bit.text, FontWeight.Medium), modifier = Modifier.padding(horizontal = 20.dp))
        Text("Your home screen may take a moment to update", style = body(13f, Bit.dim), modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcons.all.forEach { c ->
                val on = c.key == current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(70.dp).squish {
                            if (!on) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); AppIcons.set(context, c.key); current = c.key }
                        }.border(if (on) 3.dp else 0.dp, if (on) Bit.accent else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape).background(Color(c.bg)),
                    ) {
                        Image(painterResource(c.fg), c.label, Modifier.fillMaxSize().graphicsLayer { scaleX = 108f / 72f; scaleY = 108f / 72f })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.label, style = mono(10.5f, if (on) Bit.accent else Bit.dim))
                }
            }
        }
    }
}
