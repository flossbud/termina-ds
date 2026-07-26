package com.terminads.mm.secondscreen

import kotlin.math.roundToInt

/**
 * Reports the main display's rounded active-mode rate once initially and after
 * each actual change. [DisplayInfo.isDefault] is derived from
 * Display.DEFAULT_DISPLAY by SecondScreenManager.
 */
internal class MainDisplayRefreshReporter(private val submit: (Int) -> Unit) {
    private var lastRefreshHz: Int? = null

    fun refresh(displays: List<DisplayInfo>) {
        val refreshHz = displays.firstOrNull { it.isDefault }?.refreshRate?.roundToInt() ?: return
        if (refreshHz == lastRefreshHz) return

        lastRefreshHz = refreshHz
        submit(refreshHz)
    }
}
