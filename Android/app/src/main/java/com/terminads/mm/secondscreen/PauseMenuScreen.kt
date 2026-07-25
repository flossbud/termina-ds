package com.terminads.mm.secondscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * Pause root menu skeleton (handoff §5). Plan A ships the five rows with
 * RESUME live and the rest inert; Plan B brings the full §5 styling
 * (diamonds, sub-lines, warm SONG OF TIME) and lights up OPTIONS.
 */
@Composable
fun PauseMenuScreen(
    model: HudModel,
    resumePending: Boolean,
    resumeFailed: Boolean,
    onResumeTap: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            Text(
                "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
            )
        }
        MenuRow("RESUME", enabled = true, pending = resumePending, onTap = onResumeTap)
        if (resumeFailed) {
            Text(
                "RESUME FAILED",
                style = TerminaType.StallChip.toStyle(TerminaColors.GoldDim),
                modifier = Modifier
                    .padding(top = du(4f))
                    .semantics { contentDescription = "Resume failed" },
            )
        }
        MenuRow("INVENTORY", enabled = false)
        MenuRow("MAP", enabled = false)
        MenuRow("SONG OF TIME", enabled = false)
        MenuRow("OPTIONS", enabled = false)
    }
}

@Composable
private fun MenuRow(
    label: String,
    enabled: Boolean,
    pending: Boolean = false,
    onTap: () -> Unit = {},
) {
    val ink = when {
        !enabled -> TerminaColors.TextDimmer
        pending -> TerminaColors.GoldDim
        else -> TerminaColors.Ink2
    }
    Text(
        label,
        style = TerminaType.NavTab.toStyle(ink),
        modifier = Modifier
            .height(du(106f))
            .padding(du(16f))
            .clickable(enabled = enabled && !pending, onClick = onTap)
            .semantics {
                contentDescription =
                    if (enabled) label else "$label, available in a future update"
                if (!enabled) disabled()
            },
    )
}
