package com.bitdroid.tube.ui

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bitdroid.tube.R
import com.bitdroid.tube.data.Prefs

/** A Bloom tonal palette: Material You roles, all dark, with soft containers. */
data class Palette(
    val bg: Color,
    val surface: Color, // tiles / cards
    val surfaceHigh: Color, // raised tiles, tab bar
    val raised: Color, // chips, badges
    val accent: Color, // primary
    val onAccent: Color,
    val soft: Color, // primary container
    val onSoft: Color,
    val pink: Color, // tertiary container
    val onPink: Color,
    val text: Color,
    val dim: Color,
    val outline: Color,
    val glowA: Color, // wallpaper glows behind everything
    val glowB: Color,
)

object Themes {
    data class Choice(val key: String, val label: String)

    val all = listOf(
        Choice("lilac", "Lilac"),
        Choice("you", "Material You"),
        Choice("mint", "Mint"),
        Choice("peach", "Peach"),
        Choice("ocean", "Ocean"),
        Choice("rose", "Rose"),
        Choice("amoled", "Midnight"),
    )

    private fun p(
        bg: Long, surface: Long, high: Long, raised: Long,
        accent: Long, onAccent: Long, soft: Long, onSoft: Long, pink: Long, onPink: Long,
        text: Long, dim: Long, outline: Long, glowA: Long, glowB: Long,
    ) = Palette(
        Color(bg), Color(surface), Color(high), Color(raised), Color(accent), Color(onAccent), Color(soft), Color(onSoft),
        Color(pink), Color(onPink), Color(text), Color(dim), Color(outline), Color(glowA), Color(glowB),
    )

    val LILAC = p(0xFF14111C, 0xFF201B2B, 0xFF2B2539, 0xFF372F48, 0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF, 0xFF633B48, 0xFFFFD8E4, 0xFFE8E0F0, 0xFFB8AECB, 0xFF4A4458, 0xFF5C40C8, 0xFF8E3F72)
    private val MINT = p(0xFF0F1512, 0xFF19211D, 0xFF222C27, 0xFF2C3832, 0xFF7ADBB3, 0xFF003827, 0xFF005139, 0xFF96F7CE, 0xFF254B57, 0xFFC2E8F7, 0xFFDEE4DF, 0xFFA9B6AE, 0xFF3F4943, 0xFF1F7A5C, 0xFF1F5C7A)
    private val PEACH = p(0xFF19120E, 0xFF261C17, 0xFF322620, 0xFF3F312A, 0xFFFFB68D, 0xFF532200, 0xFF773300, 0xFFFFDBC9, 0xFF4F4719, 0xFFF1E2A7, 0xFFF0DFD7, 0xFFC9B3A8, 0xFF52443C, 0xFFB0552A, 0xFF8A7A20)
    private val OCEAN = p(0xFF101418, 0xFF1A2027, 0xFF242B33, 0xFF2F3740, 0xFFA0CAFD, 0xFF003258, 0xFF0E4A7D, 0xFFD1E4FF, 0xFF533F5E, 0xFFF3DAFF, 0xFFE0E2E8, 0xFFAFB8C4, 0xFF424850, 0xFF2462A8, 0xFF6A3F8A)
    private val ROSE = p(0xFF1A1113, 0xFF271C1F, 0xFF33272A, 0xFF403236, 0xFFFFB1C5, 0xFF650033, 0xFF8E244E, 0xFFFFD9E1, 0xFF5E4021, 0xFFFFDCC0, 0xFFF0DEE1, 0xFFD5BEC3, 0xFF524347, 0xFFB03A68, 0xFFA8652A)
    private val AMOLED = p(0xFF000000, 0xFF0F0D14, 0xFF1A1721, 0xFF26222F, 0xFFD0BCFF, 0xFF381E72, 0xFF3B2A66, 0xFFEADDFF, 0xFF4A2C38, 0xFFFFD8E4, 0xFFEDE7F5, 0xFFA8A0B8, 0xFF332E3D, 0xFF3A2A80, 0xFF000000)

    fun current(): String = Prefs.raw().getString("app_theme", "lilac") ?: "lilac"

    fun set(context: Context, key: String) {
        Prefs.raw().edit().putString("app_theme", key).apply()
        Bit.palette = palette(context, key)
    }

    fun palette(context: Context, key: String): Palette = when (key) {
        "mint" -> MINT
        "peach" -> PEACH
        "ocean" -> OCEAN
        "rose" -> ROSE
        "amoled" -> AMOLED
        "you" -> {
            // Wallpaper colors, the way Pixel does it.
            val d = dynamicDarkColorScheme(context)
            Palette(
                bg = d.surface, surface = d.surfaceContainer, surfaceHigh = d.surfaceContainerHigh, raised = d.surfaceContainerHighest,
                accent = d.primary, onAccent = d.onPrimary, soft = d.primaryContainer, onSoft = d.onPrimaryContainer,
                pink = d.tertiaryContainer, onPink = d.onTertiaryContainer, text = d.onSurface, dim = d.onSurfaceVariant,
                outline = d.outlineVariant, glowA = d.primary.copy(alpha = 0.55f), glowB = d.tertiary.copy(alpha = 0.5f),
            )
        }
        else -> LILAC
    }
}

object Bit {
    /** Snapshot state: changing it recolors the whole app live. */
    var palette by mutableStateOf(Themes.LILAC)

    fun apply(context: Context) { palette = Themes.palette(context, Themes.current()) }

    val bg get() = palette.bg
    val surface get() = palette.surface
    val high get() = palette.surfaceHigh
    val raised get() = palette.raised
    val accent get() = palette.accent
    val onAccent get() = palette.onAccent
    val soft get() = palette.soft
    val onSoft get() = palette.onSoft
    val pink get() = palette.pink
    val onPink get() = palette.onPink
    val text get() = palette.text
    val dim get() = palette.dim
    val outline get() = palette.outline
    val error = Color(0xFFFFB4AB)

    val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/** Wide display type for titles. */
val Display: FontFamily = variable(R.font.unbounded, 500, 600, 700, 800)
val Mono: FontFamily = variable(R.font.jetbrains_mono, 500, 700)
val Body: FontFamily = variable(R.font.inter, 400, 500, 600, 700, 800)

fun mono(size: Float, color: Color = Bit.dim, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = 0.4.sp)

fun body(size: Float, color: Color = Bit.text, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Body, fontSize = size.sp, fontWeight = weight, color = color)

fun display(size: Float, color: Color = Bit.text, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = Display, fontSize = size.sp, fontWeight = weight, color = color)

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Bit.accent, onPrimary = Bit.onAccent, primaryContainer = Bit.soft, onPrimaryContainer = Bit.onSoft,
        secondary = Bit.accent, onSecondary = Bit.onAccent, tertiaryContainer = Bit.pink, onTertiaryContainer = Bit.onPink,
        background = Bit.bg, onBackground = Bit.text, surface = Bit.bg, onSurface = Bit.text,
        surfaceVariant = Bit.high, onSurfaceVariant = Bit.dim, surfaceContainer = Bit.surface, surfaceContainerLow = Bit.surface,
        surfaceContainerHigh = Bit.high, surfaceContainerHighest = Bit.raised, surfaceContainerLowest = Bit.bg,
        outline = Bit.dim, outlineVariant = Bit.outline, error = Bit.error,
    )
    val base = Typography()
    val type = Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Body),
        bodyMedium = base.bodyMedium.copy(fontFamily = Body),
        bodySmall = base.bodySmall.copy(fontFamily = Body),
        labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Body),
        labelSmall = base.labelSmall.copy(fontFamily = Mono),
    )
    MaterialTheme(colorScheme = scheme, typography = type) {
        CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = Body, color = Bit.text), content = content)
    }
}

/** The bitHome-style wallpaper: two soft color glows over the tonal background. */
fun Modifier.backdrop(): Modifier = drawBehind {
    drawRect(Bit.bg)
    drawRect(Brush.radialGradient(listOf(Bit.palette.glowA.copy(alpha = 0.32f * Bit.palette.glowA.alpha), Color.Transparent), center = Offset(size.width * 0.1f, -size.height * 0.02f), radius = size.width * 1.1f))
    drawRect(Brush.radialGradient(listOf(Bit.palette.glowB.copy(alpha = 0.22f * Bit.palette.glowB.alpha), Color.Transparent), center = Offset(size.width * 1.05f, size.height * 0.95f), radius = size.width * 1.0f))
}

/** Click with a squishy press-in and bounce back (the Bloom feel). */
fun Modifier.squish(enabled: Boolean = true, scale: Float = 0.92f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) scale else 1f,
        spring(dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.45f, stiffness = if (pressed) 900f else 500f),
        label = "squish",
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}
