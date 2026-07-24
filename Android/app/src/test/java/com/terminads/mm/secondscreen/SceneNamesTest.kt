package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneNamesTest {

    @Test
    fun knownScenesResolveToTheirCuratedNames() {
        // Spot checks against mm/include/tables/scene_table.h ordinals.
        assertEquals("Southern Swamp (Clear)", SceneNames.forId(0x00))
        assertEquals("Termina Field", SceneNames.forId(0x2D))
        assertEquals("South Clock Town", SceneNames.forId(0x6F))
    }

    @Test
    fun unsetGapsAreNullNotGarbage() {
        // Ids 1-6 are DEFINE_SCENE_UNSET in the table.
        for (id in 1..6) assertNull(SceneNames.forId(id))
    }

    @Test
    fun outOfRangeIdsAreNull() {
        assertNull(SceneNames.forId(-1))
        assertNull(SceneNames.forId(999))
    }

    @Test
    fun tableCarriesEveryNamedScene() {
        // 113 ordinals in scene_table.h, 102 of them DEFINE_SCENE with a name.
        assertEquals(102, SceneNames.size)
    }
}
