package com.terminads.mm.secondscreen

/**
 * Chooses which display hosts the Termina DS second screen.
 *
 * The AYN Thor reports its 6" top panel as the default display and its 3.92"
 * bottom panel as secondary. Other dual-screen handhelds invert this, so the
 * policy keys off the default flag rather than display order, size, or id.
 *
 * The default display is never selectable: it is where the game renders.
 */
object DisplaySelectionPolicy {

    fun select(displays: List<DisplayInfo>, overrideDisplayId: Int?): DisplayInfo? {
        val candidates = displays.filterNot { it.isDefault }

        if (overrideDisplayId != null) {
            val chosen = candidates.firstOrNull { it.displayId == overrideDisplayId }
            if (chosen != null) return chosen
        }

        return candidates.firstOrNull()
    }
}
