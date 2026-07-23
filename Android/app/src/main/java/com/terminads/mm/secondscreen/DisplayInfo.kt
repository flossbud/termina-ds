package com.terminads.mm.secondscreen

/**
 * A snapshot of one Android display.
 *
 * Deliberately free of android.view.Display so selection logic is unit-testable
 * on the JVM. SecondScreenManager maps real Displays into this.
 */
data class DisplayInfo(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRate: Float,
    val isDefault: Boolean,
)
