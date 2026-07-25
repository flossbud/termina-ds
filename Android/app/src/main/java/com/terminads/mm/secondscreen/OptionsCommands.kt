package com.terminads.mm.secondscreen

import com.terminads.mm.CommandBridge
import com.terminads.mm.SubmitStatus

/**
 * Turns an Options interaction into exactly one absolute command.
 *
 * Three rows use semantic opcodes because their CVar write alone applies
 * nothing (see CommandMailbox.h). The rest write their CVar by name. Nothing
 * here reads state back: the next snapshot is the acknowledgement, exactly as
 * pause works.
 */

/** CVar names, copied from mm/2s2h/BenGui/BenMenu.cpp so the menus agree. */
private val CVAR_NAMES = mapOf(
    OptionKey.FPS to "gInterpolationFPS",
    OptionKey.MATCH_HZ to "gMatchRefreshRate",
    OptionKey.CLOCK_TYPE to "gEnhancements.Graphics.ClockType",
    OptionKey.BLUR_MODE to "gEnhancements.Graphics.MotionBlur.Mode",
    OptionKey.BLUR_STRENGTH to "gEnhancements.Graphics.MotionBlur.Strength",
    OptionKey.DRAW_DISTANCE to "gEnhancements.Graphics.IncreaseActorDrawDistance",
    OptionKey.ITEM_DROPS_3D to "gEnhancements.Graphics.3DItemDrops",
)

fun submitOptionChange(
    bridge: CommandBridge,
    key: OptionKey,
    value: Int,
): SubmitStatus = when (key) {
    OptionKey.INTERNAL_RES -> bridge.setInternalResPercent(value)
    OptionKey.MSAA -> bridge.setMsaa(value)
    OptionKey.TEXTURE_FILTER -> bridge.setTextureFilter(value)
    else -> bridge.setCVarInt(
        // Every non-semantic key is in the map; a miss is a programming error,
        // not a runtime condition, so fail loudly rather than writing "null".
        requireNotNull(CVAR_NAMES[key]) { "no CVar name for $key" },
        value,
    )
}

/**
 * Snap a slider value to its step and clamp it to the range.
 *
 * [max] stays reachable even when it is not on-step: a 144 Hz display gives the
 * FPS row max=144 with step 5, and the player must be able to select the
 * display's actual maximum.
 */
fun quantize(value: Int, min: Int, max: Int, step: Int): Int {
    if (value >= max) return max
    if (value <= min) return min
    val snapped = min + ((value - min + step / 2) / step) * step
    return snapped.coerceIn(min, max)
}

/**
 * The engine value one segment away, wrapping. A control whose live value is
 * not representable (selectedIndex -1) steps to the first segment in either
 * direction rather than guessing where the player meant to be.
 */
fun nextSegmentValue(control: OptionControl.Segmented, delta: Int): Int {
    if (control.selectedIndex !in control.values.indices) return control.values.first()
    val n = control.values.size
    return control.values[((control.selectedIndex + delta) % n + n) % n]
}

/**
 * Persists CVars 2 s after the last change, so dragging a slider does not write
 * the config file on every frame.
 *
 * Time is passed in rather than read, so the tests are deterministic and the
 * caller keeps its single source of "now".
 */
class CVarSaveDebouncer(private val windowMillis: Long = 2_000L) {
    private var dueAtMillis: Long? = null

    fun noteChange(nowMillis: Long) {
        dueAtMillis = nowMillis + windowMillis
    }

    fun dueAt(): Long? = dueAtMillis

    /** True while a save is pending and its quiet window has elapsed. */
    fun isDue(nowMillis: Long): Boolean {
        val due = dueAtMillis ?: return false
        return nowMillis >= due
    }

    /** Discharge after ring acceptance or a permanent failure that cannot benefit from retry. */
    fun clear() {
        dueAtMillis = null
    }
}
