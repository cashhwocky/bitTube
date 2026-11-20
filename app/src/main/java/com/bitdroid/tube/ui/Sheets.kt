package com.bitdroid.tube.ui

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.meta
import com.bitdroid.tube.player.Playback

/** Which sheet is open. One at a time, hosted once at the root. */
object Sheets {
    var actions by mutableStateOf<VideoRef?>(null)
    /** When opened from a playlist screen, offers "Remove from this playlist". */
    var actionsPlaylist by mutableStateOf<String?>(null)
    var actionsFromHistory by mutableStateOf(false)
    var save by mutableStateOf<VideoRef?>(null)
    var quality by mutableStateOf(false)

    fun open(v: VideoRef, playlist: String? = null, fromHistory: Boolean = false) {
        actionsPlaylist = playlist
        actionsFromHistory = fromHistory
        actions = v
    }
}

fun share(context: Context, v: VideoRef) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url).putExtra(Intent.EXTRA_SUBJECT, v.title)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BloomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Bit.surface,
        contentColor = Bit.text,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 6.dp).width(44.dp).height(5.dp).clip(CircleShape).background(Bit.outline)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(22.dp)).squish(scale = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(Shapes.Squircle).background(Bit.high), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Bit.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = body(16f, Bit.text, FontWeight.Medium), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SheetHost() {
    val context = LocalContext.current
    Sheets.actions?.let { v ->
        val close = { Sheets.actions = null }
        BloomSheet(close) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Img(v.thumb, Modifier.width(112.dp).aspectRatio(16f / 9f), RoundedCornerShape(16.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(v.title, style = body(15f, Bit.text, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.meta(), style = body(12.5f, Bit.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            SheetItem(Icons.Rounded.PlayArrow, "Play") { close(); Playback.open(v) }
            val later = Library.inPlaylist(Library.WATCH_LATER, v)
            SheetItem(Icons.Rounded.Schedule, if (later) "Remove from Watch later" else "Watch later") { Library.togglePlaylist(Library.WATCH_LATER, v); close() }
            SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { close(); Sheets.save = v }
            SheetItem(Icons.Rounded.Share, "Share") { close(); share(context, v) }
            if (v.channelUrl != null) SheetItem(Icons.Rounded.AccountCircle, "Go to ${v.channel.ifBlank { "channel" }}") {
                close(); Playback.expanded = false; Nav.go(Screen.Channel(v.channelUrl, v.channel))
            }
            Sheets.actionsPlaylist?.let { pid ->
                SheetItem(Icons.Rounded.DeleteOutline, "Remove from this playlist") { Library.removeFromPlaylist(pid, v); close() }
            }
            if (Sheets.actionsFromHistory) SheetItem(Icons.Rounded.DeleteOutline, "Remove from history") { Library.removeHistory(v); close() }
        }
    }

    Sheets.save?.let { v -> SaveSheet(v) { Sheets.save = null } }
    if (Sheets.quality) QualitySheet { Sheets.quality = false }
}

/** Add to playlist: tick the playlists it should be in, or make a new one. */
@Composable
private fun SaveSheet(v: VideoRef, onDismiss: () -> Unit) {
    val view = LocalView.current
    var name by remember { mutableStateOf("") }
    BloomSheet(onDismiss) {
        Text("Save to…", style = display(19f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Library.playlists.forEach { p ->
                val inIt = p.items.any { it.id == v.id }
                SheetItem(if (p.id == Library.WATCH_LATER) Icons.Rounded.Schedule else Icons.AutoMirrored.Rounded.PlaylistAdd, p.name, onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    Library.togglePlaylist(p.id, v)
                }, trailing = {
                    Box(
                        Modifier.size(28.dp).clip(Shapes.Sunny).background(if (inIt) Bit.accent else Bit.high),
                        contentAlignment = Alignment.Center,
                    ) { if (inIt) Icon(Icons.Rounded.Check, null, tint = Bit.onAccent, modifier = Modifier.size(17.dp)) }
                })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(Bit.high).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val create = {
                if (name.isNotBlank()) {
                    val p = Library.createPlaylist(name)
                    Library.togglePlaylist(p.id, v)
                    name = ""
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            }
            Box(Modifier.weight(1f)) {
                if (name.isEmpty()) Text("New playlist name", style = body(15.5f, Bit.dim))
                BasicTextField(
                    name, { name = it }, singleLine = true, textStyle = body(15.5f),
                    cursorBrush = SolidColor(Bit.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CookieButton(Icons.Rounded.Add, "Create", create, size = 44.dp, bg = Bit.accent, tint = Bit.onAccent)
        }
    }
}

private fun qualityLabel(h: Int) = when {
    h >= 2160 -> "4K"
    h >= 1440 -> "1440p"
    h > 0 -> "${h}p"
    else -> "Auto"
}

/** Quality and speed for the video that's playing. */
@Composable
private fun QualitySheet(onDismiss: () -> Unit) {
    val view = LocalView.current
    BloomSheet(onDismiss) {
        Text("Quality", style = display(19f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val now = if (Playback.playingHeight > 0) " · playing ${qualityLabel(Playback.playingHeight)}" else ""
        SheetItem(Icons.Rounded.HighQuality, "Auto (from Settings)$now", trailing = {
            if (Playback.chosenQuality == 0) Icon(Icons.Rounded.Check, null, tint = Bit.accent)
        }) { Playback.setQuality(0); onDismiss() }
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Playback.qualities.forEach { h ->
                SheetItem(Icons.Rounded.HighQuality, qualityLabel(h), trailing = {
                    if (Playback.chosenQuality == h) Icon(Icons.Rounded.Check, null, tint = Bit.accent)
                }) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    Playback.setQuality(h); onDismiss()
                }
            }
        }
        Row(Modifier.padding(start = 24.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, null, tint = Bit.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Speed", style = display(16f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                Chip(if (s == 1f) "Normal" else "${s}×".replace(".0×", "×"), com.bitdroid.tube.data.Prefs.speed == s, { Playback.setSpeed(s) })
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
