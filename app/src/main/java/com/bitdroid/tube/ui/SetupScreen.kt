package com.bitdroid.tube.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bitdroid.tube.R
import com.bitdroid.tube.data.Account
import com.bitdroid.tube.data.Prefs

/**
 * First-run setup. It can't be skipped: bitTube only opens once the permissions it needs are
 * granted and you're signed in. If a permission is taken away or you sign out later, setup comes back.
 */
object Setup {
    var notifications by mutableStateOf(true)
        private set
    var installs by mutableStateOf(true)
        private set
    var finished by mutableStateOf(false)
        private set

    fun refresh(context: Context) {
        notifications = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        installs = context.packageManager.canRequestPackageInstalls()
        finished = Prefs.raw().getBoolean("setup_done", false)
    }

    val permissionsOk: Boolean get() = notifications && installs
    val needed: Boolean get() = !finished || !permissionsOk || !Account.signedIn

    fun finish() {
        Prefs.raw().edit().putBoolean("setup_done", true).apply()
        finished = true
    }
}

private enum class Step { WELCOME, PERMISSIONS, SIGN_IN, DONE }

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Setup.refresh(context) }
    var started by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Setup.finished) }
    // The step always follows what's still missing, so nothing can be skipped.
    val step = when {
        !started -> Step.WELCOME
        !Setup.permissionsOk -> Step.PERMISSIONS
        !Account.signedIn -> Step.SIGN_IN
        else -> Step.DONE
    }
    Box(Modifier.fillMaxSize().backdrop()) {
        AnimatedContent(
            step,
            transitionSpec = {
                val slide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                (slideInHorizontally(slide) { it / 3 } + fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith (slideOutHorizontally(slide) { -it / 4 } + fadeOut())
            },
            label = "setup",
        ) { s ->
            when (s) {
                Step.WELCOME -> Welcome { started = true }
                Step.PERMISSIONS -> Permissions()
                Step.SIGN_IN -> SignIn()
                Step.DONE -> Done()
            }
        }
    }
}

// ------------------------------------------------------------------ shared layout

@Composable
private fun StepPage(
    index: Int,
    hero: @Composable () -> Unit,
    title: String,
    subtitle: String,
    button: String,
    enabled: Boolean,
    onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val view = LocalView.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        // progress dots: the current one is a cookie
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i ->
                val on = i == index
                val w by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f), label = "dot")
                Box(
                    Modifier.padding(horizontal = 4.dp).size((10 + 10 * w).dp)
                        .clip(if (on) Shapes.Sunny else CircleShape)
                        .background(if (i <= index) Bit.accent else Bit.raised),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(36.dp))
            hero()
            Spacer(Modifier.height(30.dp))
            if (title.isNotEmpty()) {
                Text(title, style = display(27f, weight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
            }
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = body(15f, Bit.dim), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(24.dp))
            }
            content()
            Spacer(Modifier.height(16.dp))
        }
        Box(
            Modifier.fillMaxWidth().padding(bottom = 18.dp).height(62.dp)
                .squish(enabled = enabled, scale = 0.96f) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onNext() }
                .clip(CircleShape).background(if (enabled) Bit.accent else Bit.raised),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(button, style = body(17f, if (enabled) Bit.onAccent else Bit.dim, FontWeight.Bold))
                if (enabled) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Bit.onAccent)
                }
            }
        }
    }
}

/** A slowly spinning scalloped cookie with an icon (or the logo) in a clover on top. */
@Composable
private fun Hero(icon: ImageVector? = null) {
    val t = rememberInfiniteTransition(label = "hero")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0.95f, 1.04f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp).graphicsLayer { rotationZ = spin; scaleX = breathe; scaleY = breathe }.clip(Shapes.Ripple).background(Bit.soft))
        if (icon == null) {
            Box(Modifier.size(128.dp).clip(Shapes.Squircle).background(Color(0xFFEADDFF))) {
                Image(painterResource(R.drawable.ic_fg_tube_lilac), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            }
        } else {
            Box(Modifier.size(124.dp).graphicsLayer { rotationZ = -spin * 1.4f }.clip(Shapes.Clover).background(Bit.accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Bit.onAccent, modifier = Modifier.size(50.dp).graphicsLayer { rotationZ = spin * 1.4f })
            }
        }
    }
}

// ------------------------------------------------------------------ steps

@Composable
private fun Welcome(onNext: () -> Unit) {
    StepPage(
        0, { Hero() },
        title = "", subtitle = "",
        button = "Get started", enabled = true, onNext = onNext,
    ) {
        Text(
            buildAnnotatedString {
                append("Welcome to\nbit")
                withStyle(SpanStyle(color = Bit.accent)) { append("Tube") }
            },
            style = display(32f, weight = FontWeight.ExtraBold), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text("Videos, Clips and your whole YouTube, in Bloom.\nTwo quick steps and you're in.", style = body(15.5f, Bit.dim), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, why: String, granted: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(28.dp)).background(Bit.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(Shapes.Squircle).background(if (granted) Bit.soft else Bit.high), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (granted) Bit.onSoft else Bit.accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = body(16f, Bit.text, FontWeight.SemiBold))
            Text(why, style = body(13f, Bit.dim))
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Box(Modifier.size(40.dp).clip(Shapes.Sunny).background(Bit.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Allowed", tint = Bit.onAccent, modifier = Modifier.size(22.dp))
            }
        } else {
            Text(
                "Allow", style = body(14.5f, Bit.onAccent, FontWeight.Bold),
                modifier = Modifier.squish(onClick = onAllow).clip(RoundedCornerShape(18.dp)).background(Bit.accent).padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun Permissions() {
    val context = LocalContext.current
    var askedNotif by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Setup.refresh(context)
        if (!ok && askedNotif) {
            // Asked twice and still no: Android won't show the prompt again, so open the app's notification settings.
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        askedNotif = true
    }
    StepPage(
        1, { Hero(Icons.Rounded.Lock) },
        title = "A couple of permissions",
        subtitle = "bitTube needs both of these to work. You can't continue until they're allowed.",
        button = if (Setup.permissionsOk) "Continue" else "Allow both to continue",
        enabled = Setup.permissionsOk, onNext = { Setup.refresh(context) },
    ) {
        PermissionCard(Icons.Rounded.Notifications, "Notifications", "So bitTube can tell you when an update is ready", Setup.notifications) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermissionCard(Icons.Rounded.InstallMobile, "Install app updates", "Updates download from GitHub and install right inside bitTube", Setup.installs) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }
    }
}

@Composable
private fun SignIn() {
    val context = LocalContext.current
    StepPage(
        2, { Hero(Icons.Rounded.AccountCircle) },
        title = "Sign in to YouTube",
        subtitle = "Required. Your For you page, Clips, subscriptions, likes and playlists all come from your account.",
        button = "Sign in with Google", enabled = true, onNext = { openSignIn(context) },
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Bit.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Bit.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("You sign in on Google's own page. bitTube never sees your password; it only keeps your YouTube session on this phone.", style = body(13.5f, Bit.dim))
        }
    }
}

@Composable
private fun Done() {
    val first = Account.name?.substringBefore(' ')
    StepPage(
        3, {
            val photo = Account.photo
            if (photo != null) {
                Box(Modifier.size(170.dp).clip(Shapes.Ripple).background(Bit.soft).padding(8.dp)) { Img(photo, Modifier.fillMaxSize(), Shapes.Ripple) }
            } else Hero(Icons.Rounded.Check)
        },
        title = if (first != null) "You're all set, $first" else "You're all set",
        subtitle = "Your For you page and Clips are ready.",
        button = "Start watching", enabled = true, onNext = { Setup.finish() },
    )
}
