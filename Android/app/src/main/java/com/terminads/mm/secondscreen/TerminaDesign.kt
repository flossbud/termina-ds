package com.terminads.mm.secondscreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.Box
import com.terminads.mm.R

/** Design-token colors, named from the handoff README's token table. */
object TerminaColors {
    val ScreenBackground = Color(0xFF000000)
    val Ink = Color(0xFFEFE6FF)            // active tab labels, primary values
    val Ink2 = Color(0xFFE2D6F6)           // row labels, clock
    val Ink3 = Color(0xFFD7C6F4)           // subscreen titles / idle wordmark
    val InkMuted = Color(0xFFA58ED0)       // DAY label, chevrons
    val Accent = Color(0xFFB48CE8)         // diamonds, rules, glows
    val AccentLight = Color(0xFFCBB0F2)    // countdown chip text
    val AccentBright = Color(0xFFC9A2FF)   // active tab underline
    val Gold = Color(0xFFE0BD66)           // numeric values, double-defense rim
    val GoldLight = Color(0xFFF0D488)
    val GoldDim = Color(0xFFC9B17A)        // reload/stall bar text
    val TextDim = Color(0xFF6A5F85)
    val TextDimmer = Color(0xFF544D69)     // inactive tabs
    val TextDimmest = Color(0xFF3F3950)    // footer hints
    val TextHint = Color(0xFF4F4763)       // idle caption
    val ClockDim = Color(0xFF6F6288)       // AM/PM suffix
    val HeartRed = Color(0xFFFF4D5E)
    val HeartEmptyFill = Color(0xFF17161C)
    val HeartEmptyStroke = Color(0xFF3B3846)
    val MagicGreen = Color(0xFF4ADE80)
    val MagicTrack = Color(0xFF1E1C24)
    val RupeeGreen = Color(0xFF5EC46F)
    val VitalsInk = Color(0xFFEAEAEA)      // rupee count
    val AreaInk = Color(0xFFF0EEF5)        // area label, clock digits
    val ChipBorder = Color(0x73CBB0F2)     // rgba(203,176,242,.45)
    val WarningAmberBorder = Color(0x57E0BD66) // rgba(224,189,102,.34)
}

/**
 * Variable TTFs with explicit weight instances. If either font renders at a
 * uniform light weight on device, the variation settings path failed on that
 * OEM build -- escalate rather than ship (fallback would be static-instanced
 * TTFs, a deliberate decision, not a silent one).
 */
@OptIn(ExperimentalTextApi::class)
object TerminaFonts {
    val Cinzel = FontFamily(
        Font(
            R.font.cinzel_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
        Font(
            R.font.cinzel_variable, weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)),
        ),
    )
    val ChivoMono = FontFamily(
        Font(
            R.font.chivo_mono_variable, weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.chivo_mono_variable, weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )
}

/** A text role: sizes and tracking in design px, resolved via dus() at use. */
@Immutable
data class DesignTextSpec(
    val family: FontFamily,
    val weight: FontWeight,
    val sizePx: Float,
    val trackingPx: Float = 0f,
)

/** §4 type roles plus the three delta screens (idle, diagnostic, stall chip). */
object TerminaType {
    val AreaLabel = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 20f, 3f)
    val NavTab = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 23f, 7f)
    val DayLabel = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 13f, 4f)
    val Clock = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 18f)
    val ClockSuffix = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 11f)
    val RupeeCount = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 17f)
    val CountdownChip = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Bold, 13f)
    val StallChip = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 3f)
    val IdleWordmark = DesignTextSpec(TerminaFonts.Cinzel, FontWeight.Bold, 48f)
    val IdleCaption = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 13f, 3f)
    val Diagnostic = DesignTextSpec(TerminaFonts.ChivoMono, FontWeight.Medium, 16f, 1f)
}

/** Set by DesignRoot; 1f default keeps previews and tests harmless. */
val LocalDesignScale = compositionLocalOf { 1f }

/** design px -> Dp at the current scale. */
@Composable
fun du(designPx: Float): Dp =
    with(LocalDensity.current) { (designPx * LocalDesignScale.current).toDp() }

/** design px -> TextUnit. toSp() divides by fontScale, so glyphs stay px-true. */
@Composable
fun dus(designPx: Float): TextUnit =
    with(LocalDensity.current) { (designPx * LocalDesignScale.current).toSp() }

/** design px -> raw px at the current scale (shadow offsets, blur radii). */
@Composable
fun dupx(designPx: Float): Float = designPx * LocalDesignScale.current

@Composable
fun DesignTextSpec.toStyle(color: Color, shadow: Shadow? = null): TextStyle = TextStyle(
    color = color,
    fontFamily = family,
    fontWeight = weight,
    fontSize = dus(sizePx),
    letterSpacing = if (trackingPx != 0f) dus(trackingPx) else TextUnit.Unspecified,
    shadow = shadow,
)

/**
 * Root for every second-screen state: black background, one uniform design
 * scale published for du()/dus()/dupx().
 */
@Composable
fun DesignRoot(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(TerminaColors.ScreenBackground)
    ) {
        val scale = designScale(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
        )
        CompositionLocalProvider(LocalDesignScale provides scale) { content() }
    }
}

/**
 * The handoff's pzBreathe: opacity .4 -> 1 -> .4 over 2.1 s, applied to every
 * selection diamond. 9 px rotated square, 2 px corner radius, per the token
 * table's diamond rule.
 */
@Composable
fun BreathingDiamond(
    modifier: Modifier = Modifier,
    sizePx: Float = 9f,
    color: Color = TerminaColors.Accent,
) {
    val transition = rememberInfiniteTransition(label = "pzBreathe")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pzBreatheAlpha",
    )
    Box(
        modifier
            .size(du(sizePx))
            .rotate(45f)
            .alpha(alpha)
            .background(color, RoundedCornerShape(du(2f)))
    )
}
