package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class MainDisplayRefreshReporterTest {

    private fun display(id: Int, refreshRate: Float, isDefault: Boolean) =
        DisplayInfo(
            displayId = id,
            name = "display-$id",
            widthPx = 1080,
            heightPx = if (isDefault) 1920 else 1240,
            refreshRate = refreshRate,
            isDefault = isDefault,
        )

    @Test
    fun reportsTheRoundedMainDisplayRateOnFirstObservation() {
        val submitted = mutableListOf<Int>()
        val reporter = MainDisplayRefreshReporter { submitted += it }

        reporter.refresh(
            listOf(
                display(id = 2, refreshRate = 60f, isDefault = false),
                display(id = 0, refreshRate = 120.00001f, isDefault = true),
            ),
        )

        assertEquals(listOf(120), submitted)
    }

    @Test
    fun ignoresDuplicateAndSecondaryOnlyRateChanges() {
        val submitted = mutableListOf<Int>()
        val reporter = MainDisplayRefreshReporter { submitted += it }

        reporter.refresh(
            listOf(
                display(id = 0, refreshRate = 120f, isDefault = true),
                display(id = 2, refreshRate = 60f, isDefault = false),
            ),
        )
        reporter.refresh(
            listOf(
                display(id = 0, refreshRate = 120.00001f, isDefault = true),
                display(id = 2, refreshRate = 120f, isDefault = false),
            ),
        )

        assertEquals(listOf(120), submitted)
    }

    @Test
    fun reportsWhenTheRoundedMainDisplayRateChanges() {
        val submitted = mutableListOf<Int>()
        val reporter = MainDisplayRefreshReporter { submitted += it }

        reporter.refresh(listOf(display(id = 0, refreshRate = 120f, isDefault = true)))
        reporter.refresh(listOf(display(id = 0, refreshRate = 59.99999f, isDefault = true)))

        assertEquals(listOf(120, 60), submitted)
    }
}
