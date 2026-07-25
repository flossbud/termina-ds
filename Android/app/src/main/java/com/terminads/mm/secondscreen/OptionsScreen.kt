package com.terminads.mm.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.terminads.mm.GameSettings

/**
 * The Options subscreen: handoff section 6 chrome around section 10 content.
 *
 * Both Graphics categories carry real rows; the other six show the designed
 * empty state rather than filler. Local navigation (tab, category, selected
 * row) is host state -- only pause itself is game truth.
 */
@Composable
fun OptionsScreen(
    model: HudModel,
    settings: GameSettings,
    tab: OptionsTab,
    category: OptionsCategory,
    selectedKey: OptionKey?,
    onTabSelect: (OptionsTab) -> Unit,
    onCategorySelect: (OptionsCategory) -> Unit,
    onRowSelect: (OptionKey) -> Unit,
    onRowChange: (OptionKey, Int) -> Unit,
    onBack: () -> Unit,
    onResume: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OptionsHeader(model, onBack)
        OptionsTabs(tab, onTabSelect)
        CategoryChips(categoriesFor(tab), category, onCategorySelect)

        val rows = optionRows(tab, category, settings)
        if (rows.isEmpty()) {
            EmptyState(category, Modifier.weight(1f))
        } else {
            OptionRowList(
                rows = rows,
                selectedKey = selectedKey,
                onRowSelect = onRowSelect,
                onRowChange = onRowChange,
                modifier = Modifier.weight(1f),
            )
        }

        OptionsFooter(onResume)
    }
}

@Composable
private fun OptionsHeader(model: HudModel, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(du(96f))
            .padding(horizontal = du(40f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(20f)),
    ) {
        Text(
            "‹",
            style = TerminaType.SubscreenTitle.toStyle(TerminaColors.InkMuted),
            modifier = Modifier
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back to the pause menu" },
        )
        Text("OPTIONS", style = TerminaType.SubscreenTitle.toStyle(TerminaColors.Ink3))
        Box(Modifier.weight(1f))
        PausedChip()
        Text(
            "${model.dayLabel ?: ""} · ${model.clockTime} ${model.clockSuffix}",
            style = TerminaType.IdleCaption.toStyle(TerminaColors.ClockDim),
        )
        model.hoursChip?.let { HoursChip(it) }
    }
    Hairline()
}

@Composable
private fun PausedChip() {
    // Handoff README §6 explicitly specifies a 7px radius for this chrome chip.
    Row(
        Modifier
            .border(du(1f), TerminaColors.HairlineStrong, RoundedCornerShape(du(7f)))
            .padding(horizontal = du(11f), vertical = du(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(du(6f)),
    ) {
        BreathingDiamond(sizePx = 7f, color = TerminaColors.AccentBright)
        Text("PAUSED", style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentBright))
    }
}

@Composable
private fun HoursChip(text: String) {
    // Handoff README §6 explicitly specifies a 7px radius for this chrome chip.
    Text(
        text,
        style = TerminaType.CountdownChip.toStyle(TerminaColors.AccentLight),
        modifier = Modifier
            .border(du(1f), TerminaColors.ChipBorder, RoundedCornerShape(du(7f)))
            .padding(horizontal = du(10f), vertical = du(3f)),
    )
}

@Composable
private fun OptionsTabs(tab: OptionsTab, onSelect: (OptionsTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = du(22f), start = du(44f), end = du(44f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnderlinedLabel(
            text = OptionsTab.SETTINGS.label,
            active = tab == OptionsTab.SETTINGS,
            spec = TerminaType.SubscreenTab,
            onTap = { onSelect(OptionsTab.SETTINGS) },
        )
        Box(Modifier.padding(horizontal = du(24f))) {
            Box(
                Modifier
                    .size(du(8f))
                    .rotate(45f)
                    .background(TerminaColors.HairlineStrong, RoundedCornerShape(du(2f))),
            )
        }
        UnderlinedLabel(
            text = OptionsTab.ENHANCEMENTS.label,
            active = tab == OptionsTab.ENHANCEMENTS,
            spec = TerminaType.SubscreenTab,
            onTap = { onSelect(OptionsTab.ENHANCEMENTS) },
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<OptionsCategory>,
    active: OptionsCategory,
    onSelect: (OptionsCategory) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = du(14f), start = du(44f), end = du(44f), bottom = du(6f)),
        horizontalArrangement = Arrangement.spacedBy(du(28f), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (category in categories) {
            UnderlinedLabel(
                text = category.label,
                active = category == active,
                spec = TerminaType.CategoryChip,
                activeInk = TerminaColors.GoldLight,
                activeUnderline = TerminaColors.Gold,
                inactiveInk = TerminaColors.TextDim,
                onTap = { onSelect(category) },
            )
        }
    }
}

/**
 * The design's one control shape for tabs and chips alike: a label with a 2px
 * underline, gold or lavender depending on which axis it belongs to. Never a
 * box -- the handoff's geometry rules forbid rounded cards.
 *
 * The underline is sized by IntrinsicSize.Max against the measured text, NOT by
 * estimating from character count. Phase 3's nav underline shipped an estimated
 * width, two code reviews missed it, and a photo of the device caught it in
 * seconds -- on a FLAG_SECURE screen that is the only way it surfaces.
 */
@Composable
private fun UnderlinedLabel(
    text: String,
    active: Boolean,
    spec: DesignTextSpec,
    onTap: () -> Unit,
    activeInk: Color = TerminaColors.Ink,
    activeUnderline: Color = TerminaColors.AccentBright,
    inactiveInk: Color = TerminaColors.TextDimmer,
) {
    Column(
        Modifier
            .clickable(onClick = onTap)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = spec.toStyle(if (active) activeInk else inactiveInk))
        Box(
            Modifier
                .padding(top = du(6f))
                .height(du(2f))
                .fillMaxWidth()
                .background(if (active) activeUnderline else Color.Transparent),
        )
    }
}

@Composable
private fun EmptyState(category: OptionsCategory, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = du(40f), vertical = du(14f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(category.label, style = TerminaType.OptionLabel.toStyle(TerminaColors.TextHint))
        Text(
            emptyStateFor(category),
            style = TerminaType.OptionDescription.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(top = du(10f)),
        )
    }
}

@Composable
private fun OptionsFooter(onResume: () -> Unit) {
    Hairline()
    Row(
        Modifier
            .fillMaxWidth()
            .height(du(80f))
            .padding(horizontal = du(40f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "TAP A ROW TO ADJUST IT",
            style = TerminaType.FooterHint.toStyle(TerminaColors.TextHint),
        )
        Box(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "RESUME PLAY",
                style = TerminaType.PauseAction.toStyle(TerminaColors.InkMuted),
                modifier = Modifier.clickable(onClick = onResume),
            )
            Box(
                Modifier
                    .padding(top = du(4f))
                    .height(du(2f))
                    .width(du(150f))
                    .background(TerminaColors.HairlineStrong),
            )
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(du(1f))
            .background(TerminaColors.HairlineFaint),
    )
}
