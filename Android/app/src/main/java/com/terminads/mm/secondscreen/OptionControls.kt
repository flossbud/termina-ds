package com.terminads.mm.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * The Options control library (handoff section 10, "Row anatomy").
 *
 * Selection is marked three ways and never with a box: a breathing gold
 * diamond at the left of the label, the row's top hairline brightening, and a
 * soft wash. Selection is persistent and touch-driven -- this screen has no
 * arrow keys, and a purely transient treatment would leave the row anatomy's
 * centrepiece invisible at rest and give TalkBack no focus anchor.
 *
 * Every control emits the ABSOLUTE engine value, never a delta or an index, so
 * a command means the same thing whenever it lands.
 */
@Composable
fun OptionRowList(
    rows: List<OptionRow>,
    selectedKey: OptionKey?,
    onRowSelect: (OptionKey) -> Unit,
    onRowChange: (OptionKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = du(44f), end = du(44f), top = du(4f), bottom = du(18f)),
    ) {
        for (row in rows) {
            OptionRowView(
                row = row,
                selected = row.key == selectedKey,
                onSelect = { onRowSelect(row.key) },
                onChange = { onRowChange(row.key, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OptionRowView(
    row: OptionRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelInk = if (row.enabled) TerminaColors.Ink2 else TerminaColors.MenuRowInert
    Column(
        modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    selectionWash()
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .clickable(enabled = row.enabled) {
                onSelect()
                // A checkbox row's whole area is its hit target (handoff
                // section 10). Absolute: state the target, never "toggle".
                (row.control as? OptionControl.Checkbox)?.let {
                    onChange(if (it.checked) 0 else 1)
                }
            }
            .semantics {
                contentDescription = row.semantics
                if (!row.enabled) disabled()
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(du(1f))
                .background(
                    if (selected) TerminaColors.HairlineStrong else TerminaColors.HairlineFaint,
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = du(22f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(du(26f)),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(du(10f)),
                ) {
                    BreathingDiamond(
                        color = if (selected) TerminaColors.Gold else Color.Transparent,
                    )
                    Text(row.label, style = TerminaType.OptionLabel.toStyle(labelInk))
                    row.qualifier?.let {
                        Text(
                            it,
                            style = TerminaType.OptionDescription.toStyle(TerminaColors.ClockDim),
                        )
                    }
                    row.chip?.let {
                        Text(
                            it,
                            style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight),
                            modifier = Modifier
                                .border(
                                    du(1f),
                                    TerminaColors.ChipBorder,
                                    RoundedCornerShape(du(7f)),
                                )
                                .padding(horizontal = du(8f), vertical = du(2f)),
                        )
                    }
                }
                Text(
                    row.description,
                    style = TerminaType.OptionDescription.toStyle(
                        if (row.enabled) TerminaColors.TextDim else TerminaColors.TextDimmest,
                    ),
                    modifier = Modifier.padding(start = du(22f), top = du(6f)),
                )
            }
            ControlView(row, onChange)
        }
    }
}

/** The handoff's selection wash: a soft radial from the row's left edge. */
@Composable
private fun selectionWash(): Brush = Brush.horizontalGradient(
    0f to TerminaColors.Accent.copy(alpha = 0.14f),
    0.74f to Color.Transparent,
)

@Composable
private fun ControlView(row: OptionRow, onChange: (Int) -> Unit) {
    when (val control = row.control) {
        is OptionControl.Slider -> SliderControl(row, control, onChange)
        is OptionControl.Segmented -> SegmentedControl(row, control, onChange)
        is OptionControl.Checkbox -> CheckboxControl(control, row.enabled)
    }
}

/**
 * A 300x2px hairline rail with a 16px diamond knob and a tick at the default
 * value, flanked by chevron steppers.
 *
 * The rail is draggable and OPTIMISTIC: while a finger is down the knob follows
 * it directly, so it never lags the ~100 ms poll. The override is dropped on
 * release, after which the snapshot is the only source of truth -- so a command
 * the ring dropped makes the knob visibly snap back rather than leaving the
 * screen showing a value the game never took.
 *
 * Every emitted value is absolute and quantized; nothing sends a delta.
 */
@Composable
private fun SliderControl(
    row: OptionRow,
    control: OptionControl.Slider,
    onChange: (Int) -> Unit,
) {
    val spokenLabel = row.label.lowercase()
    val railInk = if (row.enabled) TerminaColors.AccentLight else TerminaColors.TextDimmer
    val knobInk = if (row.enabled) TerminaColors.AccentLight else TerminaColors.TextDimmer
    val readoutInk = if (row.enabled) TerminaColors.Gold else TerminaColors.GoldDim
    val chevronInk = if (row.enabled) TerminaColors.InkMuted else TerminaColors.TextDimmest
    val span = (control.max - control.min).coerceAtLeast(1)

    var dragValue by remember { mutableStateOf<Int?>(null) }
    var railWidthPx by remember { mutableStateOf(0) }
    val shown = dragValue ?: control.value
    val fillFraction = ((shown - control.min).toFloat() / span).coerceIn(0f, 1f)
    val shownReadout = dragValue?.let {
        control.readout.replaceFirst(control.value.toString(), it.toString())
    } ?: control.readout

    fun emitFromOffset(offsetX: Float) {
        if (railWidthPx <= 0) return
        val raw = control.min + (offsetX / railWidthPx) * span
        val quantized = quantize(raw.toInt(), control.min, control.max, control.step)
        dragValue = quantized
        onChange(quantized)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            style = TerminaType.SubscreenTitle.toStyle(chevronInk),
            modifier = Modifier
                .size(width = du(50f), height = du(54f))
                .clickable(enabled = row.enabled) {
                    onChange(
                        quantize(shown - control.step, control.min, control.max, control.step),
                    )
                }
                .semantics { contentDescription = "Decrease $spokenLabel" },
        )
        Box(
            Modifier
                .width(du(300f))
                // Taller than the 2px rail so the whole strip is a real touch
                // target; the handoff's own rule is >= 42px on anything
                // interactive.
                .height(du(44f))
                .testTag("optionRail:${row.key.name}")
                .onSizeChanged { railWidthPx = it.width }
                .then(
                    if (row.enabled) {
                        Modifier.pointerInput(control.min, control.max, control.step) {
                            detectHorizontalDragGestures(
                                onDragEnd = { dragValue = null },
                                onDragCancel = { dragValue = null },
                            ) { change, _ -> emitFromOffset(change.position.x) }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(du(2f))
                    .background(TerminaColors.MagicTrack),
            )
            Box(
                Modifier
                    .fillMaxWidth(fillFraction)
                    .height(du(2f))
                    .background(railInk),
            )
            // Default-value tick: 1px, 9px proud of the rail either side.
            // Its position is always in-range, so unlike the centred knob this
            // padding is never negative.
            Box(
                Modifier
                    .padding(
                        start = du(
                            300f * (control.defaultValue - control.min).toFloat() / span,
                        ),
                    )
                    .width(du(1f))
                    .height(du(20f))
                    .background(TerminaColors.HairlineStrong),
            )
            Box(
                Modifier
                    // Offset permits the legitimate -8px low-end position
                    // needed to keep the 16px knob centred on the rail value.
                    .offset(x = du(300f * fillFraction - 8f))
                    .size(du(16f))
                    .rotate(45f)
                    .background(knobInk, RoundedCornerShape(du(2f))),
            )
        }
        Text(
            "›",
            style = TerminaType.SubscreenTitle.toStyle(chevronInk),
            modifier = Modifier
                .size(width = du(50f), height = du(54f))
                .clickable(enabled = row.enabled) {
                    onChange(
                        quantize(shown + control.step, control.min, control.max, control.step),
                    )
                }
                .semantics { contentDescription = "Increase $spokenLabel" },
        )
        Text(
            shownReadout,
            style = TerminaType.OptionReadout.toStyle(readoutInk),
            modifier = Modifier
                .padding(start = du(14f))
                .testTag("optionReadout:${row.key.name}"),
        )
    }
}

/**
 * Underlined segmented options. Tapping emits the engine value the segment
 * stands for, so no caller has to know the index-to-value mapping.
 */
@Composable
private fun SegmentedControl(
    row: OptionRow,
    control: OptionControl.Segmented,
    onChange: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(du(6f))) {
        control.options.forEachIndexed { index, label ->
            val active = index == control.selectedIndex
            Column(
                Modifier
                    .clickable(enabled = row.enabled) { onChange(control.values[index]) }
                    .padding(horizontal = du(10f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = TerminaType.OptionSegment.toStyle(
                        when {
                            !row.enabled -> TerminaColors.TextDimmest
                            active -> TerminaColors.GoldLight
                            else -> TerminaColors.TextDimmer
                        },
                    ),
                    modifier = Modifier.padding(vertical = du(16f)),
                )
                Box(
                    Modifier
                        .height(du(2f))
                        .width(du(58f))
                        .background(
                            if (active && row.enabled) TerminaColors.Gold else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * A 46px hairline square holding a 15px gold diamond when checked, beside the
 * state word. The row -- not this square -- is the hit target, so this carries
 * no click handler and no semantics of its own.
 */
@Composable
private fun CheckboxControl(control: OptionControl.Checkbox, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(14f)),
    ) {
        Text(
            if (control.checked) "ON" else "OFF",
            style = TerminaType.CategoryChip.toStyle(
                when {
                    !enabled -> TerminaColors.TextDimmest
                    control.checked -> TerminaColors.GoldLight
                    else -> TerminaColors.TextDimmer
                },
            ),
        )
        Box(
            Modifier
                .size(du(46f))
                .border(
                    du(1f),
                    if (control.checked && enabled) {
                        TerminaColors.Gold
                    } else {
                        TerminaColors.HairlineFaint
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (control.checked) {
                Box(
                    Modifier
                        .size(du(15f))
                        .rotate(45f)
                        .background(
                            if (enabled) TerminaColors.Gold else TerminaColors.TextDimmest,
                            RoundedCornerShape(du(2f)),
                        ),
                )
            }
        }
    }
}
