package com.terminads.mm.secondscreen

import com.terminads.mm.GameSnapshot
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudModelTest {

    // Mirrors the engine's CLOCK_TIME(hr, min) with ceiling division so the
    // floor conversion back to minutes lands on the intended minute exactly.
    private fun clockTimeU16(h: Int, m: Int): Int = ((h * 60 + m) * 0x10000 + 1439) / 1440

    private fun snapshot(
        health: Int = 48,
        healthCapacity: Int = 48,
        magic: Int = 0,
        magicCapacity: Int = 0,
        rupees: Int = 0,
        day: Int = 1,
        timeOfDay: Int = 0x4000, // 6:00 AM
        doubleDefense: Boolean = false,
        hasPlayState: Boolean = true,
        sceneId: Int = 0x2D, // Termina Field
    ) = GameSnapshot(
        frameCounter = 1, health = health, healthCapacity = healthCapacity,
        magic = magic, magicCapacity = magicCapacity, magicLevel = 0,
        rupees = rupees, playerForm = 4, equippedMask = 0, day = day,
        timeOfDay = timeOfDay, isNight = false, doubleDefense = doubleDefense,
        buttonItems = listOf(255, 255, 255, 255), buttonAmmo = listOf(0, 0, 0, 0),
        hasPlayState = hasPlayState, hasPlayer = hasPlayState, sceneId = sceneId,
        roomNum = 0, playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
    )

    // ---- clock ----

    @Test
    fun clockConvertsTheEngineDayFraction() {
        assertEquals("12:00" to "AM", clockText(0x0000))
        assertEquals("6:00" to "AM", clockText(0x4000))
        assertEquals("12:00" to "PM", clockText(0x8000))
        assertEquals("7:40" to "AM", clockText(clockTimeU16(7, 40)))
        assertEquals("11:59" to "PM", clockText(0xFFFF))
    }

    // ---- countdown (spec §5: mirrors TIME_UNTIL_MOON_CRASH, z64save.h:564;
    //      a day runs 6 AM -> 6 AM, day does NOT increment at midnight) ----

    @Test
    fun countdownAtCycleStartIsSeventyTwoHours() {
        assertEquals(72 * 60, remainingMinutes(1, 0x4000))
    }

    @Test
    fun countdownAtDayOneMorningIsSeventyHours() {
        assertEquals(4220, remainingMinutes(1, clockTimeU16(7, 40)))
    }

    @Test
    fun countdownPastMidnightStaysOnDayOne() {
        // 2:00 AM with day still 1: 20 h elapsed, 52 h left.
        assertEquals(52 * 60, remainingMinutes(1, clockTimeU16(2, 0)))
    }

    @Test
    fun dawnOfTheFinalDayIsTwentyFourHours() {
        assertEquals(24 * 60, remainingMinutes(3, 0x4000))
    }

    @Test
    fun finalMinuteFloorsToZeroHours() {
        assertEquals(1, remainingMinutes(3, clockTimeU16(5, 59)))
        val m = deriveHudModel(snapshot(day = 3, timeOfDay = clockTimeU16(5, 59)))
        assertEquals("0 H", m.hoursChip)
    }

    @Test
    fun countdownClampsToZeroPastTheDeadline() {
        assertEquals(0, remainingMinutes(4, 0x4000))
    }

    @Test
    fun preCycleStateHidesDayAndCountdown() {
        val m = deriveHudModel(snapshot(day = 0))
        assertNull(m.dayLabel)
        assertNull(m.hoursChip)
    }

    // ---- hearts ----

    @Test
    fun heartsSplitIntoFullPartialAndTotal() {
        val m = deriveHudModel(snapshot(health = 41, healthCapacity = 48))
        assertEquals(2, m.fullHearts)
        assertEquals(9, m.partialSixteenths)
        assertEquals(3, m.totalHearts)
    }

    @Test
    fun fullHealthHasNoPartialHeart() {
        val m = deriveHudModel(snapshot(health = 48, healthCapacity = 48))
        assertEquals(3, m.fullHearts)
        assertEquals(0, m.partialSixteenths)
    }

    @Test
    fun twentyHeartsSurvive() {
        val m = deriveHudModel(snapshot(health = 320, healthCapacity = 320))
        assertEquals(20, m.totalHearts)
        assertEquals(20, m.fullHearts)
    }

    @Test
    fun healthBeyondCapacityClamps() {
        val m = deriveHudModel(snapshot(health = 64, healthCapacity = 48))
        assertEquals(3, m.fullHearts)
        assertEquals(0, m.partialSixteenths)
    }

    @Test
    fun zeroCapacityMeansZeroHearts() {
        val m = deriveHudModel(snapshot(health = 0, healthCapacity = 0))
        assertEquals(0, m.totalHearts)
    }

    // ---- magic ----

    @Test
    fun magicRailHiddenUntilAcquired() {
        assertNull(deriveHudModel(snapshot(magicCapacity = 0)).magicPct)
    }

    @Test
    fun magicPercentageMatchesTheDesignDefault() {
        // 30/48 -> 62%, the handoff's own default magicPct.
        assertEquals(62, deriveHudModel(snapshot(magic = 30, magicCapacity = 48)).magicPct)
    }

    @Test
    fun magicClampsIntoRange() {
        assertEquals(100, deriveHudModel(snapshot(magic = 99, magicCapacity = 48)).magicPct)
        assertEquals(0, deriveHudModel(snapshot(magic = -5, magicCapacity = 48)).magicPct)
    }

    // ---- area ----

    @Test
    fun areaNameIsUppercasedCuratedName() {
        assertEquals("TERMINA FIELD", deriveHudModel(snapshot(sceneId = 0x2D)).areaName)
    }

    @Test
    fun unknownSceneFallsBackHonestly() {
        assertEquals("SCENE 999", deriveHudModel(snapshot(sceneId = 999)).areaName)
    }

    // ---- accessibility ----

    @Test
    fun vitalsDescriptionReadsAsProse() {
        val m = deriveHudModel(
            snapshot(
                health = 128, healthCapacity = 160, magic = 30, magicCapacity = 48,
                rupees = 218, day = 1, timeOfDay = clockTimeU16(7, 40),
            )
        )
        assertEquals(
            "8 of 10 hearts. Magic 62 percent. 218 rupees. DAY 1, 7:40 AM, 70 hours left.",
            vitalsDescription(m),
        )
    }

    @Test
    fun vitalsDescriptionOmitsWhatIsHidden() {
        val m = deriveHudModel(snapshot(day = 0, magicCapacity = 0, rupees = 0))
        assertEquals("3 of 3 hearts. 0 rupees. 6:00 AM.", vitalsDescription(m))
    }

    // ---- the spec §7/§10 structural guard ----

    @Test
    fun hudModelCarriesNoPerPollNoise() {
        val fields = HudModel::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        val expected = setOf(
            "fullHearts", "partialSixteenths", "totalHearts", "doubleDefense",
            "magicPct", "rupees", "dayLabel", "clockTime", "clockSuffix",
            "hoursChip", "areaName",
        )
        assertEquals(
            "HudModel changed. If you added a field, prove it is not per-poll " +
                "noise (frameCounter and friends re-announce under TalkBack and " +
                "defeat recomposition skipping), then update this list.",
            expected, fields,
        )
    }
}
