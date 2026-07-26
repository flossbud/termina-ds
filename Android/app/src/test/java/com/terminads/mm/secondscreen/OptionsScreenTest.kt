package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.terminads.mm.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OptionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val model = HudModel(
        fullHearts = 8, partialSixteenths = 0, totalHearts = 10, doubleDefense = false,
        magicPct = 62, rupees = 218, dayLabel = "DAY 1", clockTime = "7:40",
        clockSuffix = "AM", hoursChip = "60 H", areaName = "TERMINA FIELD",
    )

    private val settings = GameSettings(
        internalResPercent = 100, msaa = 1, fps = 20, matchRefreshRate = false,
        textureFilter = 0, clockType = 0, motionBlurMode = 0, motionBlurStrength = 180,
        actorDrawDistance = 1, threeDItemDrops = false, displayRefreshHz = 60,
    )

    private fun show(
        tab: OptionsTab = OptionsTab.SETTINGS,
        category: OptionsCategory = OptionsCategory.GRAPHICS,
        onTabSelect: (OptionsTab) -> Unit = {},
        onCategorySelect: (OptionsCategory) -> Unit = {},
        onBack: () -> Unit = {},
        onResume: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignRoot {
                OptionsScreen(
                    model = model, settings = settings, tab = tab, category = category,
                    selectedKey = null, onTabSelect = onTabSelect,
                    onCategorySelect = onCategorySelect, onRowSelect = {},
                    onRowChange = { _, _ -> }, onBack = onBack, onResume = onResume,
                )
            }
        }
    }

    @Test
    fun headerCarriesTitlePausedChipAndClock() {
        show()
        composeTestRule.onNodeWithText("OPTIONS").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
        composeTestRule.onNodeWithText("DAY 1 · 7:40 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 H").assertIsDisplayed()
    }

    @Test
    fun bothTabsRenderAndEmitOnTap() {
        var picked: OptionsTab? = null
        show(onTabSelect = { picked = it })
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        composeTestRule.onNodeWithText("ENHANCEMENTS").performClick()
        assertEquals(OptionsTab.ENHANCEMENTS, picked)
    }

    @Test
    fun activeTabAndCategoryExposeSelectedSemantics() {
        show()

        composeTestRule.onNodeWithText("SETTINGS").assertIsSelected()
        composeTestRule.onNodeWithText("ENHANCEMENTS").assertIsNotSelected()
        composeTestRule.onNodeWithText("GRAPHICS").assertIsSelected()
        composeTestRule.onNodeWithText("AUDIO").assertIsNotSelected()
    }

    @Test
    fun settingsTabShowsItsFourCategoryChips() {
        show(tab = OptionsTab.SETTINGS)
        for (label in listOf("GRAPHICS", "AUDIO", "CONTROLS", "SYSTEM")) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun enhancementsTabShowsItsOwnFourCategoryChips() {
        // Active category must be one that HAS rows: a category with an empty
        // state renders its own name as the panel title, which would collide with
        // that category's chip and make onNodeWithText ambiguous.
        show(tab = OptionsTab.ENHANCEMENTS, category = OptionsCategory.GRAPHICS)
        for (label in listOf("GRAPHICS", "GAMEPLAY", "CAMERA", "QUALITY OF LIFE")) {
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun aNonGraphicsCategoryShowsItsDesignedEmptyState() {
        show(category = OptionsCategory.AUDIO)
        composeTestRule.onNodeWithText("SETTINGS FOR AUDIO NOT DESIGNED YET").assertIsDisplayed()
    }

    @Test
    fun categoryChipsEmitOnTap() {
        var picked: OptionsCategory? = null
        show(onCategorySelect = { picked = it })
        composeTestRule.onNodeWithText("CONTROLS").performClick()
        assertEquals(OptionsCategory.CONTROLS, picked)
    }

    @Test
    fun backChevronAndResumePlayEmit() {
        var backed = false
        var resumed = false
        show(onBack = { backed = true }, onResume = { resumed = true })

        composeTestRule.onNodeWithContentDescription("Back to the pause menu").performClick()
        assert(backed)

        composeTestRule.onNodeWithText("RESUME PLAY").performClick()
        assert(resumed)
    }

    @Test
    fun categoryAndChromeActionsUseTheDesignedTouchBounds() {
        show()
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(
            rootBounds.width / DESIGN_WIDTH_PX,
            rootBounds.height / DESIGN_HEIGHT_PX,
        )
        val backBounds = composeTestRule
            .onNodeWithContentDescription("Back to the pause menu")
            .fetchSemanticsNode()
            .boundsInRoot
        val categoryBounds = composeTestRule
            .onNodeWithText("GRAPHICS")
            .fetchSemanticsNode()
            .boundsInRoot
        val resumeBounds = composeTestRule
            .onNodeWithText("RESUME PLAY")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(46f * scale, backBounds.width, 1f)
        assertEquals(52f * scale, backBounds.height, 1f)
        assertEquals(42f * scale, categoryBounds.height, 1f)
        assertEquals(52f * scale, resumeBounds.height, 1f)
    }

    @Test
    fun footerHintUsesTouchVocabularyNotControllerGlyphs() {
        show()
        composeTestRule.onNodeWithText("TAP A ROW TO ADJUST IT").assertIsDisplayed()
    }
}
