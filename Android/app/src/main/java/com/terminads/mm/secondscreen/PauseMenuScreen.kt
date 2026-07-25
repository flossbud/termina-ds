package com.terminads.mm.secondscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/** Stable identity for a row, independent of its display copy. */
enum class PauseMenuAction { RESUME, INVENTORY, MAP, SONG_OF_TIME, OPTIONS }

/**
 * The pause root menu (handoff section 5).
 *
 * RESUME and OPTIONS are live; INVENTORY, MAP and SONG OF TIME render in the
 * unselected ink with transparent diamonds and disabled semantics until later
 * phases mount them. SONG OF TIME keeps its warm gold treatment throughout --
 * it is the one row the design colours differently in both states.
 */
data class PauseMenuRow(
    val action: PauseMenuAction,
    val label: String,
    val subLine: String?,
    val enabled: Boolean,
    val pending: Boolean,
    val warm: Boolean,
    val semantics: String,
)

/**
 * The menu's structure, separated from its rendering so the row set, the
 * enabled/disabled split and the semantics are testable without Compose. This
 * is the layer that would have caught the Phase 3 nav bug at build time.
 */
fun pauseMenuRows(model: HudModel, resumePending: Boolean): List<PauseMenuRow> = listOf(
    PauseMenuRow(
        action = PauseMenuAction.RESUME,
        label = "RESUME",
        subLine = model.areaName,
        enabled = !resumePending,
        pending = resumePending,
        warm = false,
        semantics = "Resume the game",
    ),
    PauseMenuRow(
        action = PauseMenuAction.INVENTORY,
        label = "INVENTORY", subLine = null, enabled = false, pending = false, warm = false,
        semantics = "Inventory, available in a future update",
    ),
    PauseMenuRow(
        action = PauseMenuAction.MAP,
        label = "MAP", subLine = null, enabled = false, pending = false, warm = false,
        semantics = "Map, available in a future update",
    ),
    PauseMenuRow(
        action = PauseMenuAction.SONG_OF_TIME,
        label = "SONG OF TIME", subLine = null, enabled = false, pending = false, warm = true,
        semantics = "Song of Time, available in a future update",
    ),
    PauseMenuRow(
        action = PauseMenuAction.OPTIONS,
        label = "OPTIONS",
        subLine = "RESOLUTION · MSAA · FRAME RATE",
        enabled = true, pending = false, warm = false,
        semantics = "Options",
    ),
)

@Composable
fun PauseMenuScreen(
    model: HudModel,
    resumePending: Boolean,
    resumeFailed: Boolean,
    onResumeTap: () -> Unit,
    onOptionsTap: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Clock line, top:34px.
        Row(
            Modifier.padding(top = du(34f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
            )
            model.hoursChip?.let {
                Text(
                    "  |  $it LEFT",
                    style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
                )
            }
        }

        Column(
            Modifier
                .padding(top = du(40f))
                .width(du(760f)),
            verticalArrangement = Arrangement.spacedBy(du(4f)),
        ) {
            for (row in pauseMenuRows(model, resumePending)) {
                MenuRow(
                    row = row,
                    onTap = when (row.action) {
                        PauseMenuAction.RESUME -> onResumeTap
                        PauseMenuAction.OPTIONS -> onOptionsTap
                        PauseMenuAction.INVENTORY,
                        PauseMenuAction.MAP,
                        PauseMenuAction.SONG_OF_TIME -> ({})
                    },
                )
            }
        }

        if (resumeFailed) {
            Text(
                "RESUME FAILED",
                style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
                modifier = Modifier
                    .padding(top = du(12f))
                    .semantics { contentDescription = "Resume failed" },
            )
        }

        Box(Modifier.weight(1f))

        Text(
            "TAP A ROW · RESUME RETURNS TO THE GAME",
            style = TerminaType.FooterHint.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(bottom = du(38f)),
        )
    }
}

@Composable
private fun MenuRow(row: PauseMenuRow, onTap: () -> Unit) {
    val ink = when {
        row.pending -> TerminaColors.GoldDim
        row.warm && row.enabled -> TerminaColors.GoldLight
        row.warm -> TerminaColors.SongOfTimeInert
        row.enabled -> TerminaColors.MenuRowInk
        else -> TerminaColors.MenuRowInert
    }
    val glow = if (row.enabled && !row.pending) {
        Shadow(
            color = if (row.warm) TerminaColors.Gold else TerminaColors.Accent,
            offset = Offset.Zero,
            blurRadius = dupx(26f),
        )
    } else {
        null
    }
    val diamondColor = when {
        !row.enabled -> Color.Transparent
        row.warm -> TerminaColors.Gold
        else -> TerminaColors.Accent
    }

    Column(
        Modifier
            .defaultMinSize(minHeight = du(106f))
            .clickable(enabled = row.enabled, onClick = onTap)
            .semantics {
                contentDescription = row.semantics
                if (!row.enabled) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(du(24f)),
        ) {
            BreathingDiamond(color = diamondColor)
            Text(row.label, style = TerminaType.MenuRow.toStyle(ink, glow))
            BreathingDiamond(color = diamondColor)
        }
        row.subLine?.let {
            Text(
                it,
                style = TerminaType.MenuSubLine.toStyle(TerminaColors.ClockDim),
                modifier = Modifier.padding(top = du(6f)),
            )
        }
    }
}
