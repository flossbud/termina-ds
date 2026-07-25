package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignFrameTest {

    @Test
    fun nativePanelUsesTheNativeDesignFrame() {
        assertEquals(1240f to 1080f, designFramePx(1240f, 1080f))
    }

    @Test
    fun halfSizePanelUsesAHalfSizeDesignFrame() {
        assertEquals(620f to 540f, designFramePx(620f, 540f))
    }

    @Test
    fun widerPanelPillarboxesTheNativeDesignFrame() {
        assertEquals(1240f to 1080f, designFramePx(2000f, 1080f))
    }

    @Test
    fun tallerPanelLetterboxesTheNativeDesignFrame() {
        assertEquals(1240f to 1080f, designFramePx(1240f, 2000f))
    }
}
