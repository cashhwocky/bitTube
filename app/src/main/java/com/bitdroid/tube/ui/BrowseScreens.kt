package com.bitdroid.tube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitdroid.tube.data.ChannelRef
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.data.compact
import com.bitdroid.tube.data.pickImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================================================================== search

/** Shown above everything while the tab bar is in search mode: recent searches and suggestions. */
@Composable
fun SearchPanel(query: String, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(query) {
        delay(180)
        suggestions = runCatching { Repo.suggestions(query) }.getOrDefault(emptyList())
    }
    val recent = Library.searches.filter { query.isBlank() || it.contains(query, true) }.take(8)
    LazyColumn(Modifier.fillMaxSize().background(Bit.bg.copy(alpha = 0.97f)), contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header("Search", "Videos and channels") }
        items(recent, key = { "r$it" }) { q ->
            SearchLine(Icons.Rounded.History, q, { onPick(q) }, { onFill(q) }, trailing = {
                Icon(Icons.Rounded.Close, "Forget", tint = Bit.dim, modifier = Modifier.size(36.dp).squish { Library.removeSearch(q) }.padding(8.dp))
            })
        }
        items(suggestions.filter { it !in recent }, key = { "s$it" }) { q -> SearchLine(Icons.Rounded.Search, q, { onPick(q) }, { onFill(q) }) }
    }
}

@Composable
private fun SearchLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, onFill: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(20.dp)).squish(scale = 0.98f, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Bit.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = body(16f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
        Icon(Icons.Rounded.NorthWest, "Edit", tint = Bit.dim, modifier = Modifier.size(36.dp).squish(onClick = onFill).padding(8.dp))
    }
}

@Composable
fun SearchScreen(query: String) {
    var filter by rememberSaveable { mutableStateOf(Repo.Filter.ALL) }
    val list = rememberPaged(query to filter) { Repo.search(query, filter) }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    LoadMoreWhenNearEnd(state) { list.more() }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomRoom())) {
        item { Header(query, "Search", onBack = { Nav.back() }) }
        item {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Repo.Filter.entries.forEach { f -> Chip(f.label, f == filter, { filter = f }) }
            }
        }
        when {
            list.items.isEmpty() && list.loading -> item { CenterLoader() }
            list.items.isEmpty() && list.error != null -> item { ErrorState(list.error!!) { scope.launch { list.refresh() } } }
            list.items.isEmpty() -> item { MessageState(Icons.Rounded.SearchOff, "No results", "Try different words.") }
            else -> {
                entries(list.items, compact = true)
                if (list.loading) item { CenterLoader() }
            }
        }
    }
}

// ================================================================== channel

@Composable
fun ChannelScreen(url: String, fallbackName: String) {
    var channel by remember(url) { mutableStateOf<Repo.Channel?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(url) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        error = null
        try {
            channel = Repo.channel(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't open this channel."
        }
    }
    LaunchedEffect(url) { if (channel == null) load() }
    val ch = channel
    val tabs = ch?.tabs.orEmpty()
    val handler = tabs.getOrNull(tab)?.second
    val list = rememberPaged(handler?.url) { if (handler == null) com.bitdroid.tube.data.Paged(emptyList(), null, null) else Repo.channelTab(handler) }
    val state = rememberLazyListState()
    LoadMoreWhenNearEnd(state) { list.more() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomRoom())) {
            item {
                val banner = ch?.info?.banners?.let { pickImage(it, 300) }
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp, start = 14.dp, end = 14.dp).aspectRatio(3.2f).clip(RoundedCornerShape(30.dp)).background(Bit.soft)) {
                        if (banner != null) Img(banner, Modifier.fillMaxSize())
                    }
                }
            }
            item {
                val info = ch?.info
                val ref = ChannelRef(url, info?.name ?: fallbackName, info?.avatars?.let { pickImage(it, 160) }, info?.subscriberCount ?: -1)
                Column(Modifier.fillMaxWidth().offset(y = (-34).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(104.dp).clip(Shapes.forKey(url)).background(Bit.bg).padding(6.dp)) { Avatar(ref.avatar, url, 92.dp) }
                    Spacer(Modifier.height(10.dp))
                    Text(ref.name, style = display(22f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                    if (ref.subscribers >= 0) Text("${compact(ref.subscribers)} subscribers", style = mono(12.5f))
                    Spacer(Modifier.height(14.dp))
                    SubscribeButton(ref)
                    val about = info?.description?.takeIf { it.isNotBlank() }
                    if (about != null) {
                        var open by remember { mutableStateOf(false) }
                        Text(
                            about, style = body(13.5f, Bit.dim), maxLines = if (open) 30 else 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp).clip(RoundedCornerShape(20.dp)).background(Bit.surface).squish(scale = 0.98f) { open = !open }.padding(14.dp),
                        )
                    }
                }
            }
            if (tabs.size > 1) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.offset(y = (-20).dp)) {
                        items(tabs.size) { i -> Chip(tabs[i].first, i == tab, { tab = i }) }
                    }
                }
            }
            when {
                error != null -> item { ErrorState(error!!) { scope.launch { load() } } }
                ch == null || (list.items.isEmpty() && list.loading) -> item { CenterLoader() }
                list.items.isEmpty() && list.error != null -> item { ErrorState(list.error!!) { scope.launch { list.refresh() } } }
                else -> {
                    entries(list.items)
                    if (list.loading) item { CenterLoader() }
                }
            }
        }
        CookieButton(
            Icons.AutoMirrored.Rounded.ArrowBack, "Back", { Nav.back() },
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 12.dp), size = 46.dp, shape = Shapes.Circle,
        )
    }
}
