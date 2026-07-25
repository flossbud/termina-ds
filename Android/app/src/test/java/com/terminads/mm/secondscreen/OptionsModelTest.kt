package com.terminads.mm.secondscreen

import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsModelTest {

    private fun settings(
        internalResPercent: Int = 100,
        msaa: Int = 1,
        fps: Int = 20,
        matchRefreshRate: Boolean = false,
        textureFilter: Int = 0,
        clockType: Int = 0,
        motionBlurMode: Int = 0,
        motionBlurStrength: Int = 180,
        actorDrawDistance: Int = 1,
        threeDItemDrops: Boolean = false,
        displayRefreshHz: Int = 60,
    ) = GameSettings(
        internalResPercent, msaa, fps, matchRefreshRate, textureFilter, clockType,
        motionBlurMode, motionBlurStrength, actorDrawDistance, threeDItemDrops,
        displayRefreshHz,
    )

    private fun settingsGraphics(s: GameSettings) =
        optionRows(OptionsTab.SETTINGS, OptionsCategory.GRAPHICS, s)

    private fun enhancementsGraphics(s: GameSettings) =
        optionRows(OptionsTab.ENHANCEMENTS, OptionsCategory.GRAPHICS, s)

    @Test
    fun bothGraphicsCategoriesCarryFiveRowsInDesignOrder() {
        assertEquals(
            listOf(
                OptionKey.INTERNAL_RES, OptionKey.MSAA, OptionKey.FPS,
                OptionKey.MATCH_HZ, OptionKey.TEXTURE_FILTER,
            ),
            settingsGraphics(settings()).map { it.key },
        )
        assertEquals(
            listOf(
                OptionKey.CLOCK_TYPE, OptionKey.BLUR_MODE, OptionKey.DRAW_DISTANCE,
                OptionKey.BLUR_STRENGTH, OptionKey.ITEM_DROPS_3D,
            ),
            enhancementsGraphics(settings()).map { it.key },
        )
    }

    @Test
    fun everyOtherCategoryHasNoRowsAndAnEmptyState() {
        for (tab in OptionsTab.entries) {
            for (category in categoriesFor(tab)) {
                if (category == OptionsCategory.GRAPHICS) continue
                assertTrue(optionRows(tab, category, settings()).isEmpty())
                assertEquals(
                    "SETTINGS FOR ${category.label} NOT DESIGNED YET",
                    emptyStateFor(category),
                )
            }
        }
    }

    @Test
    fun resolutionSliderCarriesRangeStepAndGoldReadout() {
        val row = settingsGraphics(settings(internalResPercent = 150))[0]
        val control = row.control as OptionControl.Slider
        assertEquals(150, control.value)
        assertEquals(50, control.min)
        assertEquals(200, control.max)
        assertEquals(5, control.step)
        assertEquals(100, control.defaultValue)
        assertEquals("150%", control.readout)
    }

    @Test
    fun msaaMapsFourSegmentsOntoTheEngineSampleCounts() {
        // The engine CVar is 1..8 (BenMenu.cpp:619-632); odd sample counts are
        // not universally supported, so the screen offers OFF/2x/4x/8x.
        val control = settingsGraphics(settings(msaa = 4))[1].control as OptionControl.Segmented
        assertEquals(listOf("OFF", "2×", "4×", "8×"), control.options)
        assertEquals(2, control.selectedIndex)
        assertEquals(listOf(1, 2, 4, 8), control.values)

        assertEquals(0, (settingsGraphics(settings(msaa = 1))[1].control as OptionControl.Segmented).selectedIndex)
        assertEquals(3, (settingsGraphics(settings(msaa = 8))[1].control as OptionControl.Segmented).selectedIndex)
    }

    @Test
    fun anUnrepresentableMsaaValueSelectsNothingRatherThanLying() {
        // BenMenu can set 3, 5, 6, 7. The screen must not claim one of its own
        // four segments is active when none is.
        val control = settingsGraphics(settings(msaa = 5))[1].control as OptionControl.Segmented
        assertEquals(-1, control.selectedIndex)
    }

    @Test
    fun fpsSliderMaximumAndChipComeFromTheLiveRefreshRate() {
        val row = settingsGraphics(settings(fps = 60, displayRefreshHz = 120))[2]
        val control = row.control as OptionControl.Slider
        assertEquals(20, control.min)
        assertEquals(120, control.max)
        assertEquals("MAX 120 HZ", row.chip)
    }

    @Test
    fun fpsRowGreysOutAndRewordsWhileMatchRefreshRateIsOn() {
        val on = settingsGraphics(settings(matchRefreshRate = true))[2]
        assertFalse(on.enabled)
        assertEquals("LOCKED BY MATCH REFRESH RATE", on.description)

        val off = settingsGraphics(settings(matchRefreshRate = false))[2]
        assertTrue(off.enabled)
        assertEquals(
            "CAPS THE FRAME RATE BETWEEN 20 AND THE DISPLAY MAXIMUM",
            off.description,
        )
    }

    @Test
    fun fpsShowsTheRefreshRateWhileLocked() {
        val row = settingsGraphics(settings(fps = 30, matchRefreshRate = true, displayRefreshHz = 90))[2]
        assertEquals("90", (row.control as OptionControl.Slider).readout)
    }

    @Test
    fun blurStrengthGreysOutUnlessMotionBlurIsAlwaysOn() {
        // Mirrors BenMenu.cpp:1336-1341: the CVar Strength row is meaningful
        // only in MOTION_BLUR_ALWAYS_ON (mode index 2).
        assertFalse(enhancementsGraphics(settings(motionBlurMode = 0))[3].enabled)
        assertFalse(enhancementsGraphics(settings(motionBlurMode = 1))[3].enabled)
        assertTrue(enhancementsGraphics(settings(motionBlurMode = 2))[3].enabled)
    }

    @Test
    fun blurStrengthReadsItsRealRange() {
        val control =
            enhancementsGraphics(settings(motionBlurMode = 2, motionBlurStrength = 180))[3].control
                as OptionControl.Slider
        assertEquals(0, control.min)
        assertEquals(255, control.max)
        assertEquals(5, control.step)
        assertEquals("180", control.readout)
    }

    @Test
    fun checkboxRowsReportTheirState() {
        assertTrue((settingsGraphics(settings(matchRefreshRate = true))[3].control as OptionControl.Checkbox).checked)
        assertFalse((enhancementsGraphics(settings(threeDItemDrops = false))[4].control as OptionControl.Checkbox).checked)
    }

    @Test
    fun textureFilterOffersTheEnginesThreeModesInOrder() {
        // gfx_rendering_api.h:17 -- FILTER_THREE_POINT, FILTER_LINEAR, FILTER_NONE.
        val control = settingsGraphics(settings(textureFilter = 1))[4].control as OptionControl.Segmented
        assertEquals(listOf("THREE-POINT", "LINEAR", "NONE"), control.options)
        assertEquals(1, control.selectedIndex)
    }

    @Test
    fun everyRowHasNonEmptySemanticsNamingItsStateAndControl() {
        val rows = settingsGraphics(settings()) + enhancementsGraphics(settings())
        for (row in rows) {
            assertTrue("empty semantics for ${row.key}", row.semantics.isNotBlank())
        }
        assertEquals(
            "Internal resolution, 100 percent, slider",
            settingsGraphics(settings())[0].semantics,
        )
        assertEquals(
            "Current FPS, unavailable, locked by match refresh rate",
            settingsGraphics(settings(matchRefreshRate = true))[2].semantics,
        )
    }

    @Test
    fun semanticsCarryNoPerPollValues() {
        // Accessibility rule from spec section 7: nothing that changes at the
        // poll rate may appear in a contentDescription, or TalkBack chatters
        // continuously. "Frames per second" is domain vocabulary for the FPS
        // row and is fine; what must never appear is the frame counter or a
        // clock reading.
        val rows = settingsGraphics(settings()) + enhancementsGraphics(settings())
        for (row in rows) {
            assertFalse(row.semantics.contains("frame counter", ignoreCase = true))
            // A clock reading like "7:40" is the canonical per-poll value.
            assertFalse(row.semantics.contains(":"))
        }
    }

    @Test
    fun semanticsAreAFunctionOfSettingsAlone() {
        // The real guarantee behind the rule above: identical settings must
        // produce identical spoken text, so nothing time-varying can leak in.
        val first = settingsGraphics(settings()) + enhancementsGraphics(settings())
        val second = settingsGraphics(settings()) + enhancementsGraphics(settings())
        assertEquals(first.map { it.semantics }, second.map { it.semantics })
    }
}
