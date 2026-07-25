package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseNavStateTest {

    @Test
    fun startsAtTheRootMenuOnTheSettingsGraphicsSlice() {
        val s = PauseNavState()
        assertEquals(PauseView.ROOT, s.view)
        assertEquals(OptionsTab.SETTINGS, s.tab)
        assertEquals(OptionsCategory.GRAPHICS, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun openingOptionsAndComingBack() {
        val opened = PauseNavState().openOptions()
        assertEquals(PauseView.OPTIONS, opened.view)
        assertEquals(PauseView.ROOT, opened.back().view)
    }

    @Test
    fun switchingTabResetsCategoryAndSelection() {
        val s = PauseNavState()
            .openOptions()
            .selectCategory(OptionsCategory.CONTROLS)
            .selectRow(OptionKey.MSAA)
            .selectTab(OptionsTab.ENHANCEMENTS)

        assertEquals(OptionsTab.ENHANCEMENTS, s.tab)
        assertEquals(OptionsCategory.GRAPHICS, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun switchingCategoryResetsSelectionButKeepsTheTab() {
        val s = PauseNavState()
            .openOptions()
            .selectRow(OptionKey.MSAA)
            .selectCategory(OptionsCategory.AUDIO)

        assertEquals(OptionsTab.SETTINGS, s.tab)
        assertEquals(OptionsCategory.AUDIO, s.category)
        assertNull(s.selectedKey)
    }

    @Test
    fun selectingARowRecordsIt() {
        assertEquals(OptionKey.FPS, PauseNavState().openOptions().selectRow(OptionKey.FPS).selectedKey)
    }

    @Test
    fun returningToTheRootClearsTheRowSelection() {
        // Reopening Options must not restore a selection the player cannot see
        // the origin of.
        val s = PauseNavState().openOptions().selectRow(OptionKey.MSAA).back()
        assertNull(s.selectedKey)
    }
}
