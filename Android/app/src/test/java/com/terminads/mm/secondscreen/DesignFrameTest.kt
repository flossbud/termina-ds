package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignFrameTest {

    @Test
    fun nativePanelScalesToOne() {
        assertEquals(1f, designScale(1240f, 1080f), 1e-6f)
    }

    @Test
    fun uniformScaleUsesTheTighterAxis() {
        // Half-width panel: width is the constraint even though height fits.
        assertEquals(0.5f, designScale(620f, 1080f), 1e-6f)
    }

    @Test
    fun tallerPanelScalesByWidth() {
        assertEquals(1f, designScale(1240f, 2000f), 1e-6f)
    }
}
