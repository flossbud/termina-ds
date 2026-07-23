package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolchainSmokeTest {
    @Test
    fun kotlinSourcesCompileAndTestsRun() {
        val doubled = listOf(1, 2, 3).map { it * 2 }
        assertEquals(listOf(2, 4, 6), doubled)
    }
}
