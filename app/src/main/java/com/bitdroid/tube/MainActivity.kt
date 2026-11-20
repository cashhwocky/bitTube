package com.bitdroid.tube

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.VideoRef
import com.bitdroid.tube.data.videoId
import com.bitdroid.tube.data.watchUrl
import com.bitdroid.tube.player.ClipPlayback
import com.bitdroid.tube.player.Playback
import com.bitdroid.tube.ui.Bit
import com.bitdroid.tube.ui.BitTheme
import com.bitdroid.tube.ui.ChannelScreen
import com.bitdroid.tube.ui.FeedScreen
import com.bitdroid.tube.ui.FloatingTabBar
import com.bitdroid.tube.ui.HomeScreen
import com.bitdroid.tube.ui.LibraryScreen
import com.bitdroid.tube.ui.LocalListScreen
import com.bitdroid.tube.ui.MiniPlayer
import com.bitdroid.tube.ui.Nav
import com.bitdroid.tube.ui.Screen
import com.bitdroid.tube.ui.SearchPanel
import com.bitdroid.tube.ui.SearchScreen
import com.bitdroid.tube.ui.SettingsScreen
import com.bitdroid.tube.ui.SheetHost
import com.bitdroid.tube.ui.SubscriptionsScreen
import com.bitdroid.tube.ui.TabItem
import com.bitdroid.tube.ui.UpdateScreen
import com.bitdroid.tube.ui.WatchScreen
import com.bitdroid.tube.ui.backdrop
import com.bitdroid.tube.ui.body
import com.bitdroid.tube.update.Updater
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Bit.apply(this)
        if (savedInstanceState == null) handleIntent(intent)
        // Every launch: if GitHub has a newer build, the full-screen update page shows.
        Updater.dismissed = false
        if (com.bitdroid.tube.data.Account.signedIn) lifecycleScope.launch { com.bitdroid.tube.data.Account.refreshProfile() }
        com.bitdroid.tube.ui.Setup.refresh(this)
        lifecycleScope.launch {
            Updater.checkOnLaunch(this@MainActivity)?.let { notifyUpdate(it.versionName) }
        }

        setContent {
            BitTheme {
                val haze = remember { HazeState() }
                var searching by remember { mutableStateOf(false) }
                var query by remember { mutableStateOf("") }
                val top = Nav.top

                // Full screen video: landscape, no system bars.
                LaunchedEffect(Playback.fullscreen) { applyFullscreen(Playback.fullscreen) }
                LaunchedEffect(Playback.playing, ClipPlayback.playing) {
                    if (Playback.playing || ClipPlayback.playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                BackHandler(enabled = searching) { searching = false; query = "" }
                BackHandler(enabled = !searching && !Playback.expanded && (Nav.stack.size > 1 || top != Screen.Home)) { Nav.back() }

                if (com.bitdroid.tube.ui.Setup.needed) {
                    // First run (or a permission/sign-in went missing): setup, no way around it.
                    Box(Modifier.fillMaxSize()) {
                        com.bitdroid.tube.ui.SetupScreen()
                        if (!Updater.dismissed) UpdateScreen()
                    }
                } else Box(Modifier.fillMaxSize().backdrop()) {
                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                        AnimatedContent(
                            targetState = top to Nav.stack.size,
                            transitionSpec = {
                                val forward = targetState.second >= initialState.second
                                val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                if (targetState.second == 1 && initialState.second == 1) {
                                    (fadeIn(fade) + scaleIn(fade, initialScale = 0.96f)) togetherWith fadeOut(fade)
                                } else if (forward) {
                                    (slideInHorizontally(slide) { it / 3 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade))
                                } else {
                                    (slideInHorizontally(slide) { -it / 6 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { it / 3 } + fadeOut(fade))
                                }
                            },
                            label = "nav",
                        ) { (screen, _) ->
                            when (screen) {
                                Screen.Home -> HomeScreen()
                                Screen.Feed -> FeedScreen()
                                Screen.Library -> LibraryScreen()
                                Screen.Settings -> SettingsScreen()
                                Screen.Subscriptions -> SubscriptionsScreen()
                                Screen.Liked, Screen.History, is Screen.Playlist -> LocalListScreen(screen)
                                is Screen.Search -> SearchScreen(screen.query)
                                is Screen.Channel -> ChannelScreen(screen.url, screen.name)
                                is Screen.YtList -> com.bitdroid.tube.ui.YtListScreen(screen.browseId, screen.title)
                            }
                        }
                        if (searching) {
                            SearchPanel(query, onPick = { q ->
                                Library.addSearch(q)
                                searching = false
                                query = ""
                                Nav.go(Screen.Search(q))
                            }, onFill = { query = it })
                        }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()) {
                        AnimatedVisibility(Playback.video != null && !Playback.expanded && !searching, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                            MiniPlayer(Modifier.padding(bottom = 8.dp))
                        }
                        val tabIndex = Nav.tabs.indexOf(Nav.stack.first()).coerceAtLeast(0)
                        FloatingTabBar(
                            haze = haze,
                            tabs = listOf(
                                TabItem(Icons.Rounded.Home, "Home"),
                                TabItem(Icons.Rounded.Subscriptions, "Feed"),
                                TabItem(Icons.Rounded.VideoLibrary, "Library"),
                            ),
                            selected = tabIndex,
                            onSelect = { i -> searching = false; Nav.tab(i) },
                            searching = searching,
                            onSearch = { searching = !searching; if (!searching) query = "" },
                        ) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                            Box {
                                if (query.isEmpty()) Text("Search bitTube", style = body(17f, Bit.dim))
                                BasicTextField(
                                    query, { query = it }, singleLine = true,
                                    textStyle = body(17f),
                                    cursorBrush = SolidColor(Bit.accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        val q = query.trim()
                                        if (q.isNotEmpty()) {
                                            Library.addSearch(q)
                                            searching = false
                                            query = ""
                                            Nav.go(Screen.Search(q))
                                        }
                                    }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        Playback.video != null && Playback.expanded,
                        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                        exit = slideOutVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
                    ) { WatchScreen() }

                    SheetHost()

                    if (!Updater.dismissed) UpdateScreen()
                }
            }
        }
    }

    private fun applyFullscreen(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            c.show(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    /** "Update ready" notification, alongside the full-screen page. */
    private fun notifyUpdate(version: String) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(android.app.NotificationChannel("updates", "Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(this, "updates")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("bitTube $version is ready")
            .setContentText("Tap to update")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Bit.apply(this) // picks up wallpaper color changes for Material You
        com.bitdroid.tube.ui.Setup.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        Playback.savePosition()
        ClipPlayback.pause()
    }

    /** Opens youtube.com / youtu.be links and shared text in bitTube. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        val id = videoId(url)
        when {
            id != null -> Playback.open(VideoRef(watchUrl(id), "", "", null, "https://i.ytimg.com/vi/$id/hqdefault.jpg", null, -1, short = url.contains("/shorts/")))
            url.contains("/@") || url.contains("/channel/") || url.contains("/c/") || url.contains("/user/") -> {
                val clean = url.substringBefore('?').trimEnd('/')
                Nav.go(Screen.Channel(clean, ""))
            }
            url.contains("results?search_query=") -> {
                val q = android.net.Uri.parse(url).getQueryParameter("search_query")
                if (!q.isNullOrBlank()) Nav.go(Screen.Search(q))
            }
        }
    }
}
