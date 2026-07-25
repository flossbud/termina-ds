package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import java.util.Locale

/**
 * Everything the gameplay HUD draws, and nothing else.
 *
 * Deliberately primitives-and-Strings so it is stable for Compose and
 * structurally comparable: the HUD subtree recomposes only when a displayed
 * value changed, not at the 10 Hz poll rate. frameCounter must never appear
 * here -- HudModelTest.hudModelCarriesNoPerPollNoise enforces the field list.
 */
data class HudModel(
    val fullHearts: Int,
    /** 1/16-heart fill of the heart after the full ones; 0 = none partial. */
    val partialSixteenths: Int,
    val totalHearts: Int,
    val doubleDefense: Boolean,
    /** null = magic not yet acquired: hide the rail, don't show it empty. */
    val magicPct: Int?,
    val rupees: Int,
    /** "DAY 1"; null hidden while day < 1 (pre-cycle intro state). */
    val dayLabel: String?,
    val clockTime: String,
    val clockSuffix: String,
    /** "70 H"; null hidden while day < 1. */
    val hoursChip: String?,
    val areaName: String,
)

/** Quarter-quantized fill for each capacity heart, with at most one partial. */
fun heartFills(
    fullHearts: Int,
    partialSixteenths: Int,
    totalHearts: Int,
): List<Float> = List(totalHearts) { index ->
    when {
        index < fullHearts -> 1f
        index == fullHearts -> (partialSixteenths / 4) / 4f
        else -> 0f
    }
}

/** Heart fills partitioned into the original HUD's ten-heart rows. */
fun heartRows(fills: List<Float>): List<List<Float>> = fills.chunked(10)

/** Engine u16 day fraction (0x10000 = 24 h, 0 = midnight) -> minutes. */
internal fun minutesOfDay(timeOfDay: Int): Int = timeOfDay * 1440 / 0x10000

/** 12-hour clock text: "7:40" to "AM". */
internal fun clockText(timeOfDay: Int): Pair<String, String> {
    val minutes = minutesOfDay(timeOfDay)
    val h24 = minutes / 60
    val minute = minutes % 60
    val suffix = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return String.format(Locale.ROOT, "%d:%02d", h12, minute) to suffix
}

/**
 * Minutes until the moon falls (Day 4, 6:00 AM). Mirrors the engine's
 * TIME_UNTIL_MOON_CRASH (z64save.h:564): the day slot does NOT increment at
 * midnight -- a day runs 6 AM to 6 AM -- so elapsed time within the current
 * day is wrapped relative to 6:00 AM, not to midnight.
 */
internal fun remainingMinutes(day: Int, timeOfDay: Int): Int {
    val elapsedInDay = Math.floorMod(minutesOfDay(timeOfDay) - 360, 1440)
    return ((4 - day) * 1440 - elapsedInDay).coerceAtLeast(0)
}

fun deriveHudModel(s: GameSnapshot): HudModel {
    val capacity = s.healthCapacity.coerceAtLeast(0)
    val health = s.health.coerceIn(0, capacity)
    val (time, suffix) = clockText(s.timeOfDay)
    val inCycle = s.day >= 1
    return HudModel(
        fullHearts = health / 16,
        partialSixteenths = health % 16,
        totalHearts = capacity / 16,
        doubleDefense = s.doubleDefense,
        magicPct = if (s.magicCapacity <= 0) {
            null
        } else {
            (s.magic * 100 / s.magicCapacity).coerceIn(0, 100)
        },
        rupees = s.rupees,
        dayLabel = if (inCycle) "DAY ${s.day}" else null,
        clockTime = time,
        clockSuffix = suffix,
        hoursChip = if (inCycle) "${remainingMinutes(s.day, s.timeOfDay) / 60} H" else null,
        areaName = SceneNames.forId(s.sceneId)?.uppercase(Locale.ROOT) ?: "SCENE ${s.sceneId}",
    )
}

/**
 * The vitals bar's single TalkBack node (spec §7): prose, composed once, no
 * per-poll values. The fastest-changing token is the clock minute.
 */
fun vitalsDescription(m: HudModel): String {
    val parts = mutableListOf<String>()
    val partial = if (m.partialSixteenths > 0) " and a partial heart" else ""
    parts += "${m.fullHearts} of ${m.totalHearts} hearts$partial"
    m.magicPct?.let { parts += "Magic $it percent" }
    parts += "${m.rupees} rupees"
    val clock = "${m.clockTime} ${m.clockSuffix}"
    parts += if (m.dayLabel != null && m.hoursChip != null) {
        "${m.dayLabel}, $clock, ${m.hoursChip.removeSuffix(" H")} hours left"
    } else {
        clock
    }
    return parts.joinToString(". ") + "."
}

/** Which screen the host shows. A pure function of the bridge state. */
sealed interface ScreenKind {
    data class Gameplay(val model: HudModel, val stalledSeconds: Long?) : ScreenKind
    data class Idle(val waitingForGame: Boolean) : ScreenKind
    data class Diagnostic(val message: String) : ScreenKind
}

/**
 * Routing per spec §4. The diagnostic strings are copied verbatim from the
 * Phase 2 debug readout so docs/HANDOFF.md's fault vocabulary still matches
 * what the screen shows; RouteTest pins them.
 */
fun route(state: BridgeState): ScreenKind = when (state) {
    is BridgeState.Live ->
        if (state.snapshot.hasPlayState) {
            ScreenKind.Gameplay(deriveHudModel(state.snapshot), stalledSeconds = null)
        } else {
            ScreenKind.Idle(waitingForGame = false)
        }
    is BridgeState.Stalled ->
        if (state.snapshot.hasPlayState) {
            ScreenKind.Gameplay(deriveHudModel(state.snapshot), state.millisSinceChange / 1000)
        } else {
            ScreenKind.Idle(waitingForGame = false)
        }
    BridgeState.NoFramesYet -> ScreenKind.Idle(waitingForGame = true)
    BridgeState.NativeUnavailable -> ScreenKind.Diagnostic("NATIVE NOT LOADED")
    is BridgeState.SchemaMismatch -> ScreenKind.Diagnostic(
        "SCHEMA MISMATCH native=${state.nativeVersion} expected=${state.expected}"
    )
    is BridgeState.BufferTooSmall -> ScreenKind.Diagnostic(
        "BUFFER TOO SMALL kotlin=${state.kotlinSlots} slots < native payload " +
            "(GameSnapshotLayout must mirror GameSnapshot.h)"
    )
    BridgeState.UnknownReadStatus -> ScreenKind.Diagnostic(
        "UNKNOWN READ STATUS (native is newer than this build's Kotlin)"
    )
}
