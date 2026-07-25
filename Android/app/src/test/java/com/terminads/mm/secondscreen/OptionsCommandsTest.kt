package com.terminads.mm.secondscreen

import com.terminads.mm.CommandBridge
import com.terminads.mm.SubmitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsCommandsTest {

    private val calls = mutableListOf<List<Any?>>()
    private val bridge = CommandBridge { op, a, b, name ->
        calls += listOf(op, a, b, name)
        0
    }

    @Test
    fun theThreeApplyRowsUseTheirSemanticOpcodes() {
        submitOptionChange(bridge, OptionKey.INTERNAL_RES, 150)
        submitOptionChange(bridge, OptionKey.MSAA, 4)
        submitOptionChange(bridge, OptionKey.TEXTURE_FILTER, 1)

        assertEquals(listOf(CommandBridge.OP_SET_INTERNAL_RES, 150, 0, null), calls[0])
        assertEquals(listOf(CommandBridge.OP_SET_MSAA, 4, 0, null), calls[1])
        assertEquals(listOf(CommandBridge.OP_SET_TEXTURE_FILTER, 1, 0, null), calls[2])
    }

    @Test
    fun everyOtherRowWritesItsCVarByName() {
        submitOptionChange(bridge, OptionKey.FPS, 60)
        submitOptionChange(bridge, OptionKey.MATCH_HZ, 1)
        submitOptionChange(bridge, OptionKey.CLOCK_TYPE, 2)
        submitOptionChange(bridge, OptionKey.BLUR_MODE, 2)
        submitOptionChange(bridge, OptionKey.BLUR_STRENGTH, 200)
        submitOptionChange(bridge, OptionKey.DRAW_DISTANCE, 3)
        submitOptionChange(bridge, OptionKey.ITEM_DROPS_3D, 1)

        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 60, 0, "gInterpolationFPS"),
            calls[0],
        )
        assertEquals(listOf(CommandBridge.OP_CVAR_SET_INT, 1, 0, "gMatchRefreshRate"), calls[1])
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 2, 0, "gEnhancements.Graphics.ClockType"),
            calls[2],
        )
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 2, 0, "gEnhancements.Graphics.MotionBlur.Mode"),
            calls[3],
        )
        assertEquals(
            listOf(
                CommandBridge.OP_CVAR_SET_INT, 200, 0,
                "gEnhancements.Graphics.MotionBlur.Strength",
            ),
            calls[4],
        )
        assertEquals(
            listOf(
                CommandBridge.OP_CVAR_SET_INT, 3, 0,
                "gEnhancements.Graphics.IncreaseActorDrawDistance",
            ),
            calls[5],
        )
        assertEquals(
            listOf(CommandBridge.OP_CVAR_SET_INT, 1, 0, "gEnhancements.Graphics.3DItemDrops"),
            calls[6],
        )
    }

    @Test
    fun everyOptionKeyRoutesToACommand() {
        // Guards the gap that shipped in the plan: OptionKey.FPS existed with
        // no CVAR_NAMES entry, so its row would have thrown on first use. Any
        // future key added without a mapping fails here rather than on device.
        for (key in OptionKey.entries) {
            val calls = mutableListOf<List<Any?>>()
            val bridge = CommandBridge { op, a, b, name ->
                calls += listOf(op, a, b, name); 0
            }
            assertEquals(SubmitStatus.OK, submitOptionChange(bridge, key, 1))
            assertEquals("no command submitted for $key", 1, calls.size)
        }
    }

    @Test
    fun aNonOkStatusIsReturnedNotSwallowed() {
        val full = CommandBridge { _, _, _, _ -> 1 }
        assertEquals(SubmitStatus.FULL, submitOptionChange(full, OptionKey.MSAA, 4))
    }

    @Test
    fun quantizeSnapsToTheStepAndClampsToTheRange() {
        assertEquals(100, quantize(102, min = 50, max = 200, step = 5))
        assertEquals(105, quantize(103, min = 50, max = 200, step = 5))
        assertEquals(50, quantize(10, min = 50, max = 200, step = 5))
        assertEquals(200, quantize(999, min = 50, max = 200, step = 5))
    }

    @Test
    fun quantizeKeepsTheMaximumReachableWhenItIsOffStep() {
        // A 90 Hz display gives max=90 with step 5; 90 is on-step, but a 144 Hz
        // one gives max=144, which is not. The maximum must stay selectable.
        assertEquals(144, quantize(144, min = 20, max = 144, step = 5))
        assertEquals(144, quantize(143, min = 20, max = 144, step = 5))
        assertEquals(140, quantize(141, min = 20, max = 144, step = 5))
    }

    @Test
    fun segmentSteppingWrapsInBothDirections() {
        val control = OptionControl.Segmented(
            options = listOf("OFF", "2×", "4×", "8×"),
            values = listOf(1, 2, 4, 8),
            selectedIndex = 3,
        )
        assertEquals(1, nextSegmentValue(control, delta = 1))
        assertEquals(4, nextSegmentValue(control, delta = -1))
    }

    @Test
    fun segmentSteppingFromAnUnrepresentableValueLandsOnTheFirstSegment() {
        val control = OptionControl.Segmented(
            options = listOf("OFF", "2×"), values = listOf(1, 2), selectedIndex = -1,
        )
        assertEquals(1, nextSegmentValue(control, delta = 1))
        assertEquals(1, nextSegmentValue(control, delta = -1))
    }

    @Test
    fun debouncerIsDueOnlyAfterTheQuietWindow() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        assertNull(d.dueAt())

        d.noteChange(nowMillis = 1_000L)
        assertEquals(3_000L, d.dueAt())
        assertFalse(d.isDue(nowMillis = 2_999L))
        assertTrue(d.isDue(nowMillis = 3_000L))
    }

    @Test
    fun eachChangeRestartsTheWindow() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        d.noteChange(nowMillis = 1_000L)
        d.noteChange(nowMillis = 2_500L)
        assertEquals(4_500L, d.dueAt())
        assertFalse(d.isDue(nowMillis = 3_000L))
        assertTrue(d.isDue(nowMillis = 4_500L))
    }

    @Test
    fun clearingDischargesThePendingSaveSoItDoesNotRepeat() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        d.noteChange(nowMillis = 0L)
        assertTrue(d.isDue(nowMillis = 2_000L))
        d.clear()
        assertNull(d.dueAt())
        assertFalse(d.isDue(nowMillis = 9_000L))
    }

    @Test
    fun aPendingSaveSurvivesANonOkSubmissionUntilCleared() {
        val d = CVarSaveDebouncer(windowMillis = 2_000L)
        d.noteChange(nowMillis = 0L)

        assertTrue(d.isDue(nowMillis = 2_000L))
        // A non-OK submit does not call clear().
        assertTrue(d.isDue(nowMillis = 2_100L))
    }
}
