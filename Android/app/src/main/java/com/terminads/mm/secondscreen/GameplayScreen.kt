package com.terminads.mm.secondscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.terminads.mm.R

/**
 * Handoff §4: the bottom-screen gameplay view. Vitals bar (64), map region
 * (fills), nav (104). ITEMS and MASKS are rendered but inert until Phase 5's
 * write bridge can pause the game.
 */
@Composable
fun GameplayScreen(model: HudModel, stalledSeconds: Long?) {
    Column(Modifier.fillMaxSize()) {
        VitalsBar(model, Modifier.fillMaxWidth().height(du(64f * LEGIBILITY)))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Image(
                painter = painterResource(R.drawable.termina_map),
                contentDescription = "Map of Termina",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = model.areaName,
                style = TerminaType.AreaLabel.toStyle(
                    TerminaColors.AreaInk,
                    shadow = Shadow(
                        color = Color(0xE6000000), // rgba(0,0,0,.9)
                        offset = Offset(0f, dupx(2f)),
                        blurRadius = dupx(10f),
                    ),
                ),
                modifier = Modifier
                    .padding(start = du(30f), top = du(14f))
                    .semantics { contentDescription = "Area: ${model.areaName}" },
            )
            if (stalledSeconds != null) {
                StallChip(
                    stalledSeconds,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = du(30f), top = du(14f)),
                )
            }
        }
        NavBar(Modifier.fillMaxWidth().height(du(104f)))
    }
}

// ---- vitals bar ----

@Composable
private fun VitalsBar(model: HudModel, modifier: Modifier) {
    val description = vitalsDescription(model)
    Row(
        // One semantic node for TalkBack (spec §7): prose, not per-glyph noise.
        modifier = modifier
            .padding(horizontal = du(30f))
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeartRows(model)
        model.magicPct?.let { pct ->
            // 130x5 rail, green fill at pct%, both fully rounded.
            Box(
                Modifier
                    .padding(start = du(12f * LEGIBILITY))
                    .width(du(130f * LEGIBILITY))
                    .height(du(5f * LEGIBILITY))
                    .background(TerminaColors.MagicTrack, RoundedCornerShape(50)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct / 100f)
                        .background(TerminaColors.MagicGreen, RoundedCornerShape(50)),
                )
            }
        }
        Row(
            Modifier.padding(start = du(14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 11px rupee diamond: rotated square, 2px corner radius.
            Box(
                Modifier
                    .size(du(11f * LEGIBILITY))
                    .rotate(45f)
                    .background(
                        TerminaColors.RupeeGreen,
                        RoundedCornerShape(du(2f * LEGIBILITY)),
                    ),
            )
            Text(
                "${model.rupees}",
                style = TerminaType.RupeeCount.toStyle(TerminaColors.VitalsInk),
                modifier = Modifier.padding(start = du(8f * LEGIBILITY)),
            )
        }
        Spacer(Modifier.weight(1f))
        model.dayLabel?.let {
            Text(
                it,
                style = TerminaType.DayLabel.toStyle(TerminaColors.InkMuted),
                modifier = Modifier.padding(end = du(18f)),
            )
        }
        Row {
            Text(
                model.clockTime,
                style = TerminaType.Clock.toStyle(TerminaColors.AreaInk),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                model.clockSuffix,
                style = TerminaType.ClockSuffix.toStyle(TerminaColors.ClockDim),
                modifier = Modifier.alignByBaseline().padding(start = du(4f)),
            )
        }
        model.hoursChip?.let { chip ->
            Box(
                Modifier
                    .padding(start = du(14f))
                    .border(
                        du(1f),
                        TerminaColors.ChipBorder,
                        RoundedCornerShape(du(6f * LEGIBILITY)),
                    )
                    .padding(
                        horizontal = du(10f * LEGIBILITY),
                        vertical = du(3f * LEGIBILITY),
                    ),
            ) {
                Text(chip, style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight))
            }
        }
    }
}

@Composable
private fun HeartRows(model: HudModel) {
    val rows = heartRows(
        heartFills(model.fullHearts, model.partialSixteenths, model.totalHearts)
    )
    // 2 rows x 27px + 4.5px gap = 58.5px; centered padding of 18.75px
    // per side brings the total to the 96px legibility-height vitals bar.
    Column(verticalArrangement = Arrangement.spacedBy(du(3f * LEGIBILITY))) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(du(3f * LEGIBILITY))) {
                row.forEach { fill -> Heart(fill, model.doubleDefense) }
            }
        }
    }
}

@Composable
private fun Heart(fillFraction: Float, doubleDefense: Boolean) {
    val strokePx = dupx(1.4f)
    Canvas(Modifier.size(du(18f * LEGIBILITY))) {
        val path = heartPath(size.width)
        drawPath(path, TerminaColors.HeartEmptyFill)
        when {
            fillFraction >= 1f -> drawPath(path, TerminaColors.HeartRed)
            fillFraction > 0f -> {
                val wedge = Path().apply {
                    moveTo(size.width / 2f, size.height / 2f)
                    arcTo(
                        rect = Rect(0f, 0f, size.width, size.height),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = fillFraction * 360f,
                        forceMoveTo = false,
                    )
                    close()
                }
                clipPath(wedge) {
                    drawPath(path, TerminaColors.HeartRed)
                }
            }
        }
        when {
            // Double defense rims filled and partial hearts, per spec §5.
            doubleDefense && fillFraction > 0f ->
                drawPath(path, TerminaColors.Gold, style = Stroke(strokePx))
            // Empty hearts always keep the handoff's empty-heart stroke.
            fillFraction < 1f ->
                drawPath(path, TerminaColors.HeartEmptyStroke, style = Stroke(strokePx))
        }
    }
}

/** The handoff's exact 24-unit SVG heart, scaled into a size x size box. */
private fun heartPath(size: Float): Path = Path().apply {
    val scale = size / 24f
    moveTo(12f * scale, 21f * scale)
    cubicTo(
        12f * scale, 21f * scale,
        4.5f * scale, 16.3f * scale,
        2f * scale, 11.7f * scale,
    )
    cubicTo(
        0.2f * scale, 8.1f * scale,
        2f * scale, 4.5f * scale,
        5.5f * scale, 4.5f * scale,
    )
    relativeCubicTo(
        2f * scale, 0f,
        3.4f * scale, 1.1f * scale,
        4.5f * scale, 2.6f * scale,
    )
    relativeCubicTo(
        1.1f * scale, -1.5f * scale,
        2.5f * scale, -2.6f * scale,
        4.5f * scale, -2.6f * scale,
    )
    relativeCubicTo(
        3.5f * scale, 0f,
        5.3f * scale, 3.6f * scale,
        3.5f * scale, 7.2f * scale,
    )
    cubicTo(
        19.5f * scale, 16.3f * scale,
        12f * scale, 21f * scale,
        12f * scale, 21f * scale,
    )
    close()
}

// ---- stall chip (spec §6 delta: the handoff has no stalled concept) ----

@Composable
private fun StallChip(seconds: Long, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(
                du(1f),
                TerminaColors.WarningAmberBorder,
                RoundedCornerShape(du(6f * LEGIBILITY)),
            )
            .padding(
                horizontal = du(10f * LEGIBILITY),
                vertical = du(3f * LEGIBILITY),
            )
            // Static description: the visible text ticks once a second, and
            // per-poll/per-second values are banned from semantics (spec §7).
            .clearAndSetSemantics { contentDescription = "Bridge stalled" },
    ) {
        Text(
            "STALLED ${seconds}s",
            style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
        )
    }
}

// ---- nav ----

@Composable
private fun NavBar(modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(du(56f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab("MAP", active = true)
        NavTab("ITEMS", active = false)
        NavTab("MASKS", active = false)
    }
}

/**
 * Handoff §4 nav: 46 tall, 2px underline. Active = Ink + AccentBright
 * underline; inactive = TextDimmer + transparent, non-interactive until
 * Phase 5 can pause the game -- no click modifier at all, and TalkBack hears
 * them as disabled.
 */
@Composable
private fun NavTab(label: String, active: Boolean) {
    val ink = if (active) TerminaColors.Ink else TerminaColors.TextDimmer
    val underline = if (active) TerminaColors.AccentBright else Color.Transparent
    Column(
        Modifier
            .height(du(46f * LEGIBILITY))
            // Load-bearing: fillMaxWidth() in a wrap-content Column takes the
            // row's max constraint; intrinsic width pins it to the label.
            .width(IntrinsicSize.Min)
            .semantics {
                if (active) {
                    contentDescription = "$label tab, current view"
                } else {
                    contentDescription = "$label tab, unavailable"
                    disabled()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TerminaType.NavTab.toStyle(ink))
        Box(
            Modifier
                .fillMaxWidth()
                .height(du(2f * LEGIBILITY))
                .background(underline),
        )
    }
}
