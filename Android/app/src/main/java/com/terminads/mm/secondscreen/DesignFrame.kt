package com.terminads.mm.secondscreen

/**
 * The design handoff's reference frame (docs/design/second-screen-handoff):
 * every §4 dimension is authored in these units. The Thor's bottom panel is
 * natively 1240x1080 so the scale is ~1.0 there, but nothing assumes it.
 */
const val DESIGN_WIDTH_PX = 1240f
const val DESIGN_HEIGHT_PX = 1080f

/**
 * One uniform scale factor from the actual panel to the design frame, using
 * the tighter axis so nothing stretches. Pure so the JVM tests can hold it.
 */
fun designScale(panelWidthPx: Float, panelHeightPx: Float): Float =
    minOf(panelWidthPx / DESIGN_WIDTH_PX, panelHeightPx / DESIGN_HEIGHT_PX)
