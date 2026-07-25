package com.terminads.mm.secondscreen

import com.terminads.mm.GameSettings

/**
 * The Options subscreen's content model (handoff section 10), sorted on two
 * axes: kind (tab) and category (chip).
 *
 * Pure -- no Compose, no Android. The screen renders this; the tests assert on
 * it. Rows are derived from the schema-v3 settings block, so a dropped command
 * corrects itself on the next poll without any reconciliation logic here.
 *
 * The handoff's Enhancements/Graphics rows (Widescreen, High-Res Texture Pack,
 * Anisotropic Filtering, Post Sharpening, Draw Distance Fog) have no backing
 * CVars in 2S2H. That tab is re-sourced from the real ones in
 * mm/2s2h/BenGui/BenMenu.cpp:1299-1392, keeping the design's row anatomy and
 * control mix. See the spec's section 10 for the full deviation list.
 */
enum class OptionsTab(val label: String) {
    SETTINGS("SETTINGS"),
    ENHANCEMENTS("ENHANCEMENTS"),
}

enum class OptionsCategory(val label: String) {
    GRAPHICS("GRAPHICS"),
    AUDIO("AUDIO"),
    CONTROLS("CONTROLS"),
    SYSTEM("SYSTEM"),
    GAMEPLAY("GAMEPLAY"),
    CAMERA("CAMERA"),
    QUALITY_OF_LIFE("QUALITY OF LIFE"),
}

/** Stable identity for a row, independent of its position or label. */
enum class OptionKey {
    INTERNAL_RES, MSAA, FPS, MATCH_HZ, TEXTURE_FILTER,
    CLOCK_TYPE, BLUR_MODE, DRAW_DISTANCE, BLUR_STRENGTH, ITEM_DROPS_3D,
}

sealed interface OptionControl {
    /**
     * A 300px hairline rail with a diamond knob. [defaultValue] draws the 1px
     * tick; [readout] is the gold Cinzel numeral beside it.
     */
    data class Slider(
        val value: Int,
        val min: Int,
        val max: Int,
        val step: Int,
        val defaultValue: Int,
        val readout: String,
    ) : OptionControl

    /**
     * Underlined segmented options. [values] holds the engine value each
     * segment writes, so the screen never has to know the mapping.
     * [selectedIndex] is -1 when the live engine value is not representable by
     * any segment -- the screen shows no active underline rather than claiming
     * a wrong one.
     */
    data class Segmented(
        val options: List<String>,
        val values: List<Int>,
        val selectedIndex: Int,
    ) : OptionControl

    /** A 46px hairline square holding a 15px gold diamond when checked. */
    data class Checkbox(val checked: Boolean) : OptionControl
}

/**
 * One row of the Options list. [qualifier] is the small mono word beside the
 * label ("MSAA"); [chip] is the bordered chip ("MAX 60 HZ").
 */
data class OptionRow(
    val key: OptionKey,
    val label: String,
    val description: String,
    val control: OptionControl,
    val enabled: Boolean,
    val qualifier: String? = null,
    val chip: String? = null,
    val semantics: String,
)

fun categoriesFor(tab: OptionsTab): List<OptionsCategory> = when (tab) {
    OptionsTab.SETTINGS -> listOf(
        OptionsCategory.GRAPHICS, OptionsCategory.AUDIO,
        OptionsCategory.CONTROLS, OptionsCategory.SYSTEM,
    )
    OptionsTab.ENHANCEMENTS -> listOf(
        OptionsCategory.GRAPHICS, OptionsCategory.GAMEPLAY,
        OptionsCategory.CAMERA, OptionsCategory.QUALITY_OF_LIFE,
    )
}

fun emptyStateFor(category: OptionsCategory): String =
    "SETTINGS FOR ${category.label} NOT DESIGNED YET"

private val MSAA_VALUES = listOf(1, 2, 4, 8)
private val MSAA_LABELS = listOf("OFF", "2×", "4×", "8×")
private val TEXTURE_FILTER_LABELS = listOf("THREE-POINT", "LINEAR", "NONE")
private val CLOCK_TYPE_LABELS = listOf("ORIGINAL", "MM3D", "TEXT ONLY")
private val BLUR_MODE_LABELS = listOf("DYNAMIC", "OFF", "ALWAYS ON")
private val DRAW_DISTANCE_LABELS = listOf("1×", "2×", "3×", "4×", "5×")

/** MOTION_BLUR_ALWAYS_ON, per BenMenu.cpp:1341. */
private const val BLUR_MODE_ALWAYS_ON = 2

fun optionRows(
    tab: OptionsTab,
    category: OptionsCategory,
    settings: GameSettings,
): List<OptionRow> = when {
    category != OptionsCategory.GRAPHICS -> emptyList()
    tab == OptionsTab.SETTINGS -> settingsGraphicsRows(settings)
    else -> enhancementsGraphicsRows(settings)
}

private fun settingsGraphicsRows(s: GameSettings): List<OptionRow> {
    val fpsLocked = s.matchRefreshRate
    val fpsShown = if (fpsLocked) s.displayRefreshHz else s.fps
    return listOf(
        OptionRow(
            key = OptionKey.INTERNAL_RES,
            label = "INTERNAL RESOLUTION",
            description = "RENDERS ABOVE NATIVE, THEN DOWNSAMPLES · DEFAULT 100%",
            control = OptionControl.Slider(
                value = s.internalResPercent, min = 50, max = 200, step = 5,
                defaultValue = 100, readout = "${s.internalResPercent}%",
            ),
            enabled = true,
            semantics = "Internal resolution, ${s.internalResPercent} percent, slider",
        ),
        OptionRow(
            key = OptionKey.MSAA,
            label = "ANTI-ALIASING",
            qualifier = "MSAA",
            description = "SMOOTHS POLYGON EDGES · HIGHER LEVELS COST FILL RATE",
            control = OptionControl.Segmented(
                options = MSAA_LABELS,
                values = MSAA_VALUES,
                selectedIndex = MSAA_VALUES.indexOf(s.msaa),
            ),
            enabled = true,
            semantics = "Anti-aliasing, ${msaaSpoken(s.msaa)}, segmented control",
        ),
        OptionRow(
            key = OptionKey.FPS,
            label = "CURRENT FPS",
            chip = "MAX ${s.displayRefreshHz} HZ",
            description = if (fpsLocked) {
                "LOCKED BY MATCH REFRESH RATE"
            } else {
                "CAPS THE FRAME RATE BETWEEN 20 AND THE DISPLAY MAXIMUM"
            },
            control = OptionControl.Slider(
                value = fpsShown, min = 20, max = s.displayRefreshHz, step = 5,
                defaultValue = 20, readout = "$fpsShown",
            ),
            enabled = !fpsLocked,
            semantics = if (fpsLocked) {
                "Current FPS, unavailable, locked by match refresh rate"
            } else {
                "Current FPS, $fpsShown frames per second, slider"
            },
        ),
        OptionRow(
            key = OptionKey.MATCH_HZ,
            label = "MATCH REFRESH RATE",
            description = "FOLLOWS THE DISPLAY AND LOCKS THE FPS CAP",
            control = OptionControl.Checkbox(s.matchRefreshRate),
            enabled = true,
            semantics = "Match refresh rate, ${onOff(s.matchRefreshRate)}, checkbox",
        ),
        OptionRow(
            key = OptionKey.TEXTURE_FILTER,
            label = "TEXTURE FILTER",
            description = "THREE-POINT MATCHES THE ORIGINAL HARDWARE BLUR",
            control = OptionControl.Segmented(
                options = TEXTURE_FILTER_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.textureFilter.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Texture filter, ${spoken(TEXTURE_FILTER_LABELS, s.textureFilter)}, " +
                "segmented control",
        ),
    )
}

private fun enhancementsGraphicsRows(s: GameSettings): List<OptionRow> {
    val strengthLive = s.motionBlurMode == BLUR_MODE_ALWAYS_ON
    return listOf(
        OptionRow(
            key = OptionKey.CLOCK_TYPE,
            label = "CLOCK TYPE",
            description = "SWAPS THE IN-GAME CLOCK BETWEEN ITS THREE TREATMENTS",
            control = OptionControl.Segmented(
                options = CLOCK_TYPE_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.clockType.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Clock type, ${spoken(CLOCK_TYPE_LABELS, s.clockType)}, segmented control",
        ),
        OptionRow(
            key = OptionKey.BLUR_MODE,
            label = "MOTION BLUR",
            description = "DYNAMIC FOLLOWS THE ORIGINAL GAME'S OWN BLUR TRIGGERS",
            control = OptionControl.Segmented(
                options = BLUR_MODE_LABELS,
                values = listOf(0, 1, 2),
                selectedIndex = s.motionBlurMode.takeIf { it in 0..2 } ?: -1,
            ),
            enabled = true,
            semantics = "Motion blur, ${spoken(BLUR_MODE_LABELS, s.motionBlurMode)}, " +
                "segmented control",
        ),
        OptionRow(
            key = OptionKey.DRAW_DISTANCE,
            label = "ACTOR DRAW DISTANCE",
            description = "DRAWS ACTORS FARTHER OUT · MAY HAVE SIDE EFFECTS",
            control = OptionControl.Segmented(
                options = DRAW_DISTANCE_LABELS,
                values = listOf(1, 2, 3, 4, 5),
                selectedIndex = (s.actorDrawDistance - 1).takeIf { it in 0..4 } ?: -1,
            ),
            enabled = true,
            semantics = "Actor draw distance, ${s.actorDrawDistance} times, segmented control",
        ),
        OptionRow(
            key = OptionKey.BLUR_STRENGTH,
            label = "MOTION BLUR STRENGTH",
            description = if (strengthLive) {
                "HOW MUCH OF THE PREVIOUS FRAME PERSISTS · 0 TO 255"
            } else {
                "LOCKED UNLESS MOTION BLUR IS ALWAYS ON"
            },
            control = OptionControl.Slider(
                value = s.motionBlurStrength, min = 0, max = 255, step = 5,
                defaultValue = 180, readout = "${s.motionBlurStrength}",
            ),
            enabled = strengthLive,
            semantics = if (strengthLive) {
                "Motion blur strength, ${s.motionBlurStrength} of 255, slider"
            } else {
                "Motion blur strength, unavailable, locked unless motion blur is always on"
            },
        ),
        OptionRow(
            key = OptionKey.ITEM_DROPS_3D,
            label = "3D ITEM DROPS",
            description = "DRAWS DROPPED ITEMS AS MODELS INSTEAD OF FLAT SPRITES",
            control = OptionControl.Checkbox(s.threeDItemDrops),
            enabled = true,
            semantics = "3D item drops, ${onOff(s.threeDItemDrops)}, checkbox",
        ),
    )
}

private fun onOff(value: Boolean) = if (value) "on" else "off"

private fun spoken(labels: List<String>, index: Int): String =
    labels.getOrNull(index)?.lowercase() ?: "unknown"

private fun msaaSpoken(value: Int): String =
    if (value == 1) "off" else "$value times"
