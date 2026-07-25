package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OptionControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun settings(
        msaa: Int = 1,
        matchRefreshRate: Boolean = false,
        motionBlurMode: Int = 0,
    ) = GameSettings(
        internalResPercent = 100, msaa = msaa, fps = 20,
        matchRefreshRate = matchRefreshRate, textureFilter = 0, clockType = 0,
        motionBlurMode = motionBlurMode, motionBlurStrength = 180,
        actorDrawDistance = 1, threeDItemDrops = false, displayRefreshHz = 60,
    )

    private var lastChange: Pair<OptionKey, Int>? = null
    private var lastSelect: OptionKey? = null

    private fun show(
        tab: OptionsTab = OptionsTab.SETTINGS,
        s: GameSettings = settings(),
        selectedKey: OptionKey? = null,
    ) {
        composeTestRule.setContent {
            DesignRoot {
                OptionRowList(
                    rows = optionRows(tab, OptionsCategory.GRAPHICS, s),
                    selectedKey = selectedKey,
                    onRowSelect = { lastSelect = it },
                    onRowChange = { key, value -> lastChange = key to value },
                )
            }
        }
    }

    @Test
    fun everyRowRendersItsLabelAndDescription() {
        show()
        composeTestRule.onNodeWithText("INTERNAL RESOLUTION").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("RENDERS ABOVE NATIVE, THEN DOWNSAMPLES · DEFAULT 100%")
            .assertIsDisplayed()
    }

    @Test
    fun theSliderReadoutRenders() {
        show()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun tappingASegmentEmitsItsEngineValueNotItsIndex() {
        show()
        // MSAA segments are OFF/2x/4x/8x -> engine values 1/2/4/8.
        composeTestRule.onNodeWithText("4×").performClick()
        assertEquals(OptionKey.MSAA to 4, lastChange)
    }

    @Test
    fun tappingARowSelectsIt() {
        show()
        composeTestRule.onNodeWithText("MATCH REFRESH RATE").performClick()
        assertEquals(OptionKey.MATCH_HZ, lastSelect)
    }

    @Test
    fun tappingACheckboxRowTogglesItAbsolutely() {
        show(s = settings(matchRefreshRate = false))
        composeTestRule.onNodeWithContentDescription(
            "Match refresh rate, off, checkbox",
        ).performClick()
        assertEquals(OptionKey.MATCH_HZ to 1, lastChange)
    }

    @Test
    fun chevronsStepTheSliderByItsStep() {
        show(selectedKey = OptionKey.INTERNAL_RES)
        composeTestRule.onNodeWithContentDescription("Increase internal resolution").performClick()
        assertEquals(OptionKey.INTERNAL_RES to 105, lastChange)

        composeTestRule.onNodeWithContentDescription("Decrease internal resolution").performClick()
        assertEquals(OptionKey.INTERNAL_RES to 95, lastChange)
    }

    @Test
    fun aDisabledRowIsInertAndAnnouncesItself() {
        lastChange = null
        lastSelect = null
        show(s = settings(matchRefreshRate = true))
        composeTestRule
            .onNodeWithContentDescription("Current FPS, unavailable, locked by match refresh rate")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        assertNull(lastSelect)
        assertNull(lastChange)
    }

    @Test
    fun aDisabledRowsChevronsAreInert() {
        lastChange = null
        lastSelect = null
        show(s = settings(matchRefreshRate = true))

        composeTestRule
            .onNodeWithContentDescription("Increase current fps", useUnmergedTree = true)
            .performTouchInput { click() }
        composeTestRule
            .onNodeWithContentDescription("Decrease current fps", useUnmergedTree = true)
            .performTouchInput { click() }

        assertNull(lastSelect)
        assertNull(lastChange)
    }

    @Test
    fun theLockedFpsRowShowsItsLockedDescription() {
        show(s = settings(matchRefreshRate = true))
        composeTestRule.onNodeWithText("LOCKED BY MATCH REFRESH RATE").assertIsDisplayed()
    }

    @Test
    fun blurStrengthIsInertUnlessMotionBlurIsAlwaysOn() {
        show(tab = OptionsTab.ENHANCEMENTS, s = settings(motionBlurMode = 0))
        composeTestRule
            .onNodeWithContentDescription(
                "Motion blur strength, unavailable, locked unless motion blur is always on",
            )
            .assertIsNotEnabled()
    }

    @Test
    fun draggingTheRailIsOptimisticThenReconcilesToTheSnapshotOnRelease() {
        show()
        val rail = composeTestRule.onNodeWithTag(
            "optionRail:INTERNAL_RES",
            useUnmergedTree = true,
        )
        val readout = composeTestRule.onNodeWithTag(
            "optionReadout:INTERNAL_RES",
            useUnmergedTree = true,
        )

        // Leave the gesture active across the assertion: the settings snapshot
        // remains at 100, but the local optimistic override follows the finger.
        rail.performTouchInput {
            down(center)
            moveTo(percentOffset(0.8f, 0.5f))
        }
        readout.assertTextEquals("170%")

        val (key, value) = requireNotNull(lastChange)
        assertEquals(OptionKey.INTERNAL_RES, key)
        assertEquals(0, value % 5)
        assertTrue(value in 50..200)

        // No new settings arrived. Releasing must drop the override so a
        // command rejected by the ring visibly snaps back to game truth.
        rail.performTouchInput { up() }
        readout.assertTextEquals("100%")
    }

    @Test
    fun aDisabledRowsRailDoesNotRespondToDrag() {
        lastChange = null
        show(s = settings(matchRefreshRate = true))
        composeTestRule
            .onNodeWithTag("optionRail:FPS", useUnmergedTree = true)
            .performTouchInput { swipeRight() }
        assertNull(lastChange)
    }
}
