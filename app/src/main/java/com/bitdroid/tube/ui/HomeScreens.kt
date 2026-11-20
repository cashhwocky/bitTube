package com.bitdroid.tube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.Entry
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.player.Playback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Room at the bottom for the floating tab bar (and the mini player when it's up). */
@Composable
fun bottomRoom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (Playback.video != null) 180.dp else 104.dp)

fun LazyListScope.entries(list: List<Entry>, compact: Boolean = false, playlist: String? = null, history: Boolean = false) {
    items(list, key = { e -> when (e) { is Entry.Video -> "v" + e.v.url; is Entry.Channel -> "c" + e.c.url } }) { e ->
        when (e) {
            is Entry.Video -> if (compact) {
                VideoRow(e.v, { Playback.open(e.v) }, { Sheets.open(e.v, playlist, history) })
            } else {
                VideoTile(e.v, { Playback.open(e.v) }, { Sheets.open(e.v, playlist, history) })
            }
            is Entry.Channel -> ChannelRow(e.c, { Nav.go(Screen.Channel(e.c.url, e.c.name)) }) { SubscribeButton(e.c) }
        }
    }
}

@Composable
fun SubscribeButton(c: com.bitdroid.tube.data.ChannelRef, modifier: Modifier = Modifier) {
    val subbed = Library.isSubscribed(c.url)
    Text(
        if (subbed) "Subscribed" else "Subscribe",
        style = body(14f, if (subbed) Bit.text else Bit.onAccent, FontWeight.Bold),
        modifier = modifier.squish { Library.toggleSubscribe(c) }.clip(RoundedCornerShape(18.dp))
            .background(if (subbed) Bit.raised else Bit.accent).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ================================================================== home

private sealed interface HomeSource {
    data object ForYou : HomeSource
    data class Kiosk(val id: String) : HomeSource
    data class Topic(val label: String, val query: String) : HomeSource
}

private val topics = listOf(
    "Music" to "music", "Gaming" to "gaming", "Tech" to "tech review", "Science" to "science explained",
    "News" to "news today", "Comedy" to "comedy", "Cooking" to "cooking recipes", "Sports" to "sports highlights",
    "Learning" to "learn something new", "Cars" to "cars", "Space" to "space", "Podcasts" to "podcast",
)

@Composable
fun HomeScreen() {
    val sources = remember {
        listOf<HomeSource>(HomeSource.ForYou) + Repo.kiosks().map { HomeSource.Kiosk(it) } + topics.map { HomeSource.Topic(it.first, it.second) }
    }
    var pick by rememberSaveable { mutableStateOf(0) }
    val source = sources[pick.coerceIn(0, sources.lastIndex)]
    val list = rememberPaged(source) {
        when (source) {
            HomeSource.ForYou -> Repo.forYou()
            is HomeSource.Kiosk -> Repo.kiosk(source.id)
            is HomeSource.Topic -> Repo.search(source.query, Repo.Filter.VIDEOS)
        }
    }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    LoadMoreWhenNearEnd(state) { list.more() }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomRoom())) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("bit")
                        withStyle(SpanStyle(color = Bit.accent)) { append("Tube") }
                    },
                    style = display(28f, weight = FontWeight.ExtraBold),
                    modifier = Modifier.weight(1f),
                )
                CookieButton(Icons.Rounded.Refresh, "Refresh", { scope.launch { list.refresh() } }, shape = Shapes.Flower)
                Spacer(Modifier.width(8.dp))
                CookieButton(Icons.Rounded.Settings, "Settings", { Nav.go(Screen.Settings) }, shape = Shapes.Sunny, bg = Bit.soft, tint = Bit.onSoft)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources.size) { i ->
                    val s = sources[i]
                    val label = when (s) {
                        HomeSource.ForYou -> "For you"
                        is HomeSource.Kiosk -> Repo.kioskLabel(s.id)
                        is HomeSource.Topic -> s.label
                    }
                    Chip(label, i == pick, { pick = i }, icon = if (s == HomeSource.ForYou) Icons.Rounded.AutoAwesome else null)
                }
            }
        }
        if (source == HomeSource.ForYou && Library.subscriptions.isEmpty() && Library.history.isEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp)).background(Bit.soft).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Bit.onSoft, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Watch and subscribe to things and this page learns what you like. Nothing leaves your phone.", style = body(14f, Bit.onSoft))
                }
            }
        }
        when {
            list.items.isEmpty() && list.loading -> item { CenterLoader() }
            list.items.isEmpty() && list.error != null -> item { ErrorState(list.error!!) { scope.launch { list.refresh() } } }
            else -> {
                entries(list.items)
                if (list.loading) item { CenterLoader() }
            }
        }
    }
}

// ================================================================== feed (subscriptions)

private object FeedCache {
    val items = mutableStateListOf<VideoRef>()
    var loadedFor: Set<String> = emptySet()
}

@Composable
fun FeedScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val subs = Library.subscriptions
    suspend fun load() {
        loading = true
        error = null
        try {
            val r = Repo.feed(subs.toList())
            FeedCache.items.clear()
            FeedCache.items.addAll(r.filter { !it.short })
            FeedCache.loadedFor = subs.map { it.url }.toSet()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't refresh your feed."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(subs.size) {
        if (subs.isNotEmpty() && FeedCache.loadedFor != subs.map { it.url }.toSet()) load()
    }
    val shown = FeedCache.items.filter { v -> filter == null || v.channelUrl?.trimEnd('/') == filter?.trimEnd('/') }
    val state = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomRoom())) {
        item {
            Header("Feed", if (subs.isEmpty()) null else "${subs.size} subscriptions") {
                CookieButton(Icons.Rounded.Group, "Manage", { Nav.go(Screen.Subscriptions) }, shape = Shapes.Flower)
                CookieButton(Icons.Rounded.Refresh, "Refresh", { scope.launch { load() } }, shape = Shapes.Sunny, bg = Bit.soft, tint = Bit.onSoft)
            }
        }
        if (subs.isEmpty()) {
            item {
                MessageState(Icons.Rounded.Subscriptions, "Nothing here yet", "Subscribe to channels and their new videos land here, newest first.")
            }
            return@LazyColumn
        }
        item {
            // Creator strip: tap a cookie to filter the feed to that channel, tap again to clear.
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(subs, key = { it.url }) { c ->
                    val on = filter == c.url
                    Column(Modifier.width(70.dp).squish { filter = if (on) null else c.url }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(66.dp).clip(Shapes.forKey(c.url)).background(if (on) Bit.accent else androidx.compose.ui.graphics.Color.Transparent).padding(4.dp)) {
                            Avatar(c.avatar, c.url, 58.dp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(c.name, style = body(11.5f, if (on) Bit.accent else Bit.dim, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        when {
            shown.isEmpty() && loading -> item { CenterLoader() }
            shown.isEmpty() && error != null -> item { ErrorState(error!!) { scope.launch { load() } } }
            else -> {
                if (loading) item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CookieLoader(size = 30.dp) } }
                entries(shown.map { Entry.Video(it) })
            }
        }
    }
}

@Composable
fun SubscriptionsScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header("Subscriptions", "${Library.subscriptions.size} channels", onBack = { Nav.back() }) }
        if (Library.subscriptions.isEmpty()) item { MessageState(Icons.Rounded.Subscriptions, "No subscriptions", "Find a channel you like and tap Subscribe.") }
        items(Library.subscriptions.toList(), key = { it.url }) { c ->
            ChannelRow(c, { Nav.go(Screen.Channel(c.url, c.name)) }) { SubscribeButton(c) }
        }
    }
}

// ================================================================== library

@Composable
private fun LibraryTile(icon: ImageVector, title: String, count: Int, shape: Shape, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.squish(scale = 0.95f, onClick = onClick).clip(RoundedCornerShape(30.dp)).background(Bit.surface).padding(16.dp)) {
        Box(Modifier.size(52.dp).clip(shape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(25.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = body(16f, Bit.text, FontWeight.SemiBold))
        Text(if (count == 1) "1 video" else "$count videos", style = body(13f, Bit.dim))
    }
}

@Composable
fun LibraryScreen() {
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item {
            Header("Library") {
                CookieButton(Icons.Rounded.Settings, "Settings", { Nav.go(Screen.Settings) }, shape = Shapes.Sunny, bg = Bit.soft, tint = Bit.onSoft)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryTile(Icons.Rounded.Favorite, "Liked", Library.liked.size, Shapes.Flower, Bit.pink, Bit.onPink, Modifier.weight(1f)) { Nav.go(Screen.Liked) }
                    val later = Library.playlists.firstOrNull { it.id == Library.WATCH_LATER }
                    LibraryTile(Icons.Rounded.Schedule, "Watch later", later?.items?.size ?: 0, Shapes.Clover, Bit.soft, Bit.onSoft, Modifier.weight(1f)) { Nav.go(Screen.Playlist(Library.WATCH_LATER)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryTile(Icons.Rounded.History, "History", Library.history.size, Shapes.Sunny, Bit.raised, Bit.accent, Modifier.weight(1f)) { Nav.go(Screen.History) }
                    LibraryTile(Icons.Rounded.Subscriptions, "Subscriptions", Library.subscriptions.size, Shapes.Gem, Bit.raised, Bit.accent, Modifier.weight(1f)) { Nav.go(Screen.Subscriptions) }
                }
            }
        }
        if (Library.history.isNotEmpty()) {
            item { SectionLabel("Keep watching") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Library.history.filter { !it.short }.take(15), key = { it.url }) { v ->
                        Column(Modifier.width(200.dp).squish(scale = 0.95f) { Playback.open(v) }) {
                            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp))) {
                                Img(v.thumb, Modifier.fillMaxSize())
                                DurationBadge(v, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(v.title, style = body(13.5f, Bit.text, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Playlists", Modifier.weight(1f))
                CookieButton(Icons.Rounded.Add, "New playlist", { naming = !naming }, size = 40.dp, bg = Bit.accent, tint = Bit.onAccent, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (naming) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(Bit.high).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (newName.isEmpty()) Text("Playlist name", style = body(15.5f, Bit.dim))
                        androidx.compose.foundation.text.BasicTextField(
                            newName, { newName = it }, singleLine = true, textStyle = body(15.5f),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Bit.accent), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PillButton("Create", { if (newName.isNotBlank()) { Library.createPlaylist(newName); newName = ""; naming = false } })
                }
            }
        }
        val lists = Library.playlists.filter { it.id != Library.WATCH_LATER }
        if (lists.isEmpty() && !naming) {
            item { Text("Make playlists with + or from any video's Save button.", style = body(14f, Bit.dim), modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
        }
        items(lists, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).clip(RoundedCornerShape(24.dp)).background(Bit.surface)
                    .squish(scale = 0.97f) { Nav.go(Screen.Playlist(p.id)) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(18.dp)).background(Bit.raised), contentAlignment = Alignment.Center) {
                    val cover = p.items.firstOrNull()?.thumb
                    if (cover != null) Img(cover, Modifier.fillMaxSize()) else Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = Bit.accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = body(15.5f, Bit.text, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (p.items.size == 1) "1 video" else "${p.items.size} videos", style = body(13f, Bit.dim))
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VideoLibrary, null, tint = Bit.dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Your library is stored on this phone only.", style = body(12.5f, Bit.dim))
            }
        }
    }
}

/** Liked, History, or one of your playlists. */
@Composable
fun LocalListScreen(screen: Screen) {
    val (title, videos, pid) = when (screen) {
        Screen.Liked -> Triple("Liked", Library.liked.toList(), null)
        Screen.History -> Triple("History", Library.history.toList(), null)
        is Screen.Playlist -> {
            val p = Library.playlists.firstOrNull { it.id == screen.id }
            Triple(p?.name ?: "Playlist", p?.items ?: emptyList(), screen.id)
        }
        else -> Triple("", emptyList(), null)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item {
            Header(title, if (videos.size == 1) "1 video" else "${videos.size} videos", onBack = { Nav.back() }) {
                if (videos.isNotEmpty()) CookieButton(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play all", { Playback.open(videos.first()) }, bg = Bit.accent, tint = Bit.onAccent, shape = Shapes.Clover)
                if (screen == Screen.History && videos.isNotEmpty()) CookieButton(Icons.Rounded.History, "Clear history", { Library.clearHistory() }, shape = Shapes.Sunny)
                if (screen is Screen.Playlist && screen.id != Library.WATCH_LATER) {
                    CookieButton(Icons.Rounded.DeleteOutline, "Delete playlist", { Library.deletePlaylist(screen.id); Nav.back() }, shape = Shapes.Sunny)
                }
            }
        }
        if (videos.isEmpty()) {
            item {
                when (screen) {
                    Screen.Liked -> MessageState(Icons.Rounded.Favorite, "No liked videos", "Tap the heart under a video and it shows up here.")
                    Screen.History -> MessageState(Icons.Rounded.History, "No history", "Videos you watch show up here.")
                    else -> MessageState(Icons.AutoMirrored.Rounded.PlaylistPlay, "Empty playlist", "Use Save under any video to add it here.")
                }
            }
        }
        entries(videos.map { Entry.Video(it) }, compact = true, playlist = pid, history = screen == Screen.History)
    }
}
