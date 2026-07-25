package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class PauseMenuScreenTest {

    private fun model(areaName: String = "TERMINA FIELD") = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = areaName,
    )

    @Test
    fun theFiveDesignRowsAppearInOrder() {
        assertEquals(
            listOf("RESUME", "INVENTORY", "MAP", "SONG OF TIME", "OPTIONS"),
            pauseMenuRows(model(), resumePending = false).map { it.label },
        )
    }

    @Test
    fun onlyResumeAndOptionsAreLive() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.action }
        assertTrue(rows.getValue(PauseMenuAction.RESUME).enabled)
        assertTrue(rows.getValue(PauseMenuAction.OPTIONS).enabled)
        assertFalse(rows.getValue(PauseMenuAction.INVENTORY).enabled)
        assertFalse(rows.getValue(PauseMenuAction.MAP).enabled)
        assertFalse(rows.getValue(PauseMenuAction.SONG_OF_TIME).enabled)
    }

    @Test
    fun subLinesAppearOnLiveRowsOnly() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.action }
        // The handoff's "AUTOSAVED 4 MIN AGO" clause is dropped: no autosave
        // data exists (spec section 10, deviation 7).
        assertEquals("TERMINA FIELD", rows.getValue(PauseMenuAction.RESUME).subLine)
        assertEquals(
            "RESOLUTION · MSAA · FRAME RATE",
            rows.getValue(PauseMenuAction.OPTIONS).subLine,
        )
        assertNull(rows.getValue(PauseMenuAction.INVENTORY).subLine)
        assertNull(rows.getValue(PauseMenuAction.MAP).subLine)
        assertNull(rows.getValue(PauseMenuAction.SONG_OF_TIME).subLine)
    }

    @Test
    fun songOfTimeKeepsItsWarmTreatmentEvenWhileInert() {
        val row = pauseMenuRows(model(), resumePending = false)
            .single { it.action == PauseMenuAction.SONG_OF_TIME }
        assertTrue(row.warm)
        assertFalse(row.enabled)
    }

    @Test
    fun inertRowsAnnounceThemselvesAsFutureWork() {
        val rows = pauseMenuRows(model(), resumePending = false).associateBy { it.action }
        assertEquals(
            "Inventory, available in a future update",
            rows.getValue(PauseMenuAction.INVENTORY).semantics,
        )
        assertEquals("Resume the game", rows.getValue(PauseMenuAction.RESUME).semantics)
        assertEquals("Options", rows.getValue(PauseMenuAction.OPTIONS).semantics)
    }

    @Test
    fun resumeReportsPendingWithoutBecomingUnavailable() {
        val row = pauseMenuRows(model(), resumePending = true).first()
        assertTrue(row.pending)
        assertFalse(row.enabled)
    }

    @Test
    fun theSubLineFollowsTheSceneName() {
        val rows = pauseMenuRows(model(areaName = "CLOCK TOWN"), resumePending = false)
        assertEquals("CLOCK TOWN", rows.first().subLine)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class PauseMenuScreenRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val model = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = "TERMINA FIELD",
    )

    @Test
    fun allFiveRowsRenderAndOnlyTheLiveOnesRespondToTaps() {
        var resumed = false
        var options = false
        composeTestRule.setContent {
            DesignRoot {
                PauseMenuScreen(
                    model = model, resumePending = false, resumeFailed = false,
                    onResumeTap = { resumed = true }, onOptionsTap = { options = true },
                )
            }
        }

        for (label in listOf("RESUME", "INVENTORY", "MAP", "SONG OF TIME", "OPTIONS")) {
            composeTestRule.onNodeWithText(label).assertExists()
        }

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        assertTrue(options)

        composeTestRule.onNodeWithContentDescription("Resume the game").performClick()
        assertTrue(resumed)
    }

    @Test
    fun inertRowsAreMarkedUnavailableToAccessibility() {
        composeTestRule.setContent {
            DesignRoot {
                PauseMenuScreen(
                    model = model, resumePending = false, resumeFailed = false,
                    onResumeTap = {}, onOptionsTap = {},
                )
            }
        }
        listOf(
            "Inventory, available in a future update",
            "Map, available in a future update",
            "Song of Time, available in a future update",
        ).forEach { description ->
            composeTestRule
                .onNodeWithContentDescription(description)
                .assertIsNotEnabled()
        }
    }
}
