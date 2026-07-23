package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionPolicyTest {

    private fun display(id: Int, isDefault: Boolean, name: String = "display-$id") =
        DisplayInfo(
            displayId = id,
            name = name,
            widthPx = 1080,
            heightPx = if (isDefault) 1920 else 1240,
            refreshRate = if (isDefault) 120f else 60f,
            isDefault = isDefault,
        )

    @Test
    fun returnsNullWhenOnlyTheDefaultDisplayExists() {
        val result = DisplaySelectionPolicy.select(listOf(display(0, true)), null)
        assertNull(result)
    }

    @Test
    fun returnsNullWhenThereAreNoDisplays() {
        assertNull(DisplaySelectionPolicy.select(emptyList(), null))
    }

    @Test
    fun picksTheFirstNonDefaultDisplay() {
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, null)?.displayId)
    }

    @Test
    fun picksNonDefaultEvenWhenItIsListedFirst() {
        // Some handhelds enumerate the secondary display before the primary.
        val displays = listOf(display(2, false), display(0, true))
        assertEquals(2, DisplaySelectionPolicy.select(displays, null)?.displayId)
    }

    @Test
    fun overrideWinsWhenItMatchesANonDefaultDisplay() {
        val displays = listOf(display(0, true), display(2, false), display(3, false))
        assertEquals(3, DisplaySelectionPolicy.select(displays, 3)?.displayId)
    }

    @Test
    fun overrideIsIgnoredWhenItNamesTheDefaultDisplay() {
        // Never take over the screen the game is running on.
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, 0)?.displayId)
    }

    @Test
    fun overrideIsIgnoredWhenItNamesAnAbsentDisplay() {
        val displays = listOf(display(0, true), display(2, false))
        assertEquals(2, DisplaySelectionPolicy.select(displays, 99)?.displayId)
    }
}
