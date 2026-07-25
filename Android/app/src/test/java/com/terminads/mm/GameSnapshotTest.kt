package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotTest {

    /** A well-formed payload with every slot zeroed except the schema version. */
    private fun emptyPayload() = IntArray(GameSnapshotLayout.SLOT_COUNT).also {
        it[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
    }

    private fun decodeOk(values: IntArray): GameSnapshot {
        val result = decodeSnapshot(values)
        assertTrue("expected Ok but was $result", result is SnapshotDecode.Ok)
        return (result as SnapshotDecode.Ok).snapshot
    }

    @Test
    fun slotCountMatchesSchemaV3() {
        assertEquals(3, GameSnapshotLayout.SCHEMA_VERSION)
        assertEquals(39, GameSnapshotLayout.SLOT_COUNT)
        assertEquals(38, GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ)
    }

    @Test
    fun decodesTheSettingsBlock() {
        val raw = IntArray(GameSnapshotLayout.SLOT_COUNT)
        raw[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 3
        raw[GameSnapshotLayout.IDX_CVAR_INTERNAL_RES] = 150
        raw[GameSnapshotLayout.IDX_CVAR_MSAA] = 4
        raw[GameSnapshotLayout.IDX_CVAR_FPS] = 60
        raw[GameSnapshotLayout.IDX_CVAR_MATCH_HZ] = 1
        raw[GameSnapshotLayout.IDX_CVAR_TEXTURE_FILTER] = 2
        raw[GameSnapshotLayout.IDX_CVAR_CLOCK_TYPE] = 1
        raw[GameSnapshotLayout.IDX_CVAR_BLUR_MODE] = 2
        raw[GameSnapshotLayout.IDX_CVAR_BLUR_STRENGTH] = 180
        raw[GameSnapshotLayout.IDX_CVAR_DRAW_DISTANCE] = 3
        raw[GameSnapshotLayout.IDX_CVAR_3D_ITEM_DROPS] = 1
        raw[GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ] = 120

        val decoded = decodeSnapshot(raw) as SnapshotDecode.Ok
        val s = decoded.snapshot.settings
        assertEquals(150, s.internalResPercent)
        assertEquals(4, s.msaa)
        assertEquals(60, s.fps)
        assertEquals(true, s.matchRefreshRate)
        assertEquals(2, s.textureFilter)
        assertEquals(1, s.clockType)
        assertEquals(2, s.motionBlurMode)
        assertEquals(180, s.motionBlurStrength)
        assertEquals(3, s.actorDrawDistance)
        assertEquals(true, s.threeDItemDrops)
        assertEquals(120, s.displayRefreshHz)
    }

    @Test
    fun aV2PayloadIsReportedAsAMismatchNotDecoded() {
        val raw = IntArray(GameSnapshotLayout.SLOT_COUNT)
        raw[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 2
        val decoded = decodeSnapshot(raw)
        assertEquals(SnapshotDecode.SchemaMismatch(2, 3), decoded)
    }

    @Test
    fun slotCountMatchesTheDocumentedLayout() {
        // Guards the hand-written mirror of mm/2s2h/TerminaDS/GameSnapshot.h.
        assertEquals(39, GameSnapshotLayout.SLOT_COUNT)
        assertEquals(3, GameSnapshotLayout.SCHEMA_VERSION)
        assertEquals(0, GameSnapshotLayout.IDX_SCHEMA_VERSION)
        assertEquals(26, GameSnapshotLayout.IDX_PLAYER_YAW)
        assertEquals(27, GameSnapshotLayout.IDX_PAUSE_STATE)
        assertEquals(1 shl 4, GameSnapshotLayout.FLAG_SAVE_LOADED)
        assertEquals(1 shl 5, GameSnapshotLayout.FLAG_MENU_OPEN)
    }

    @Test
    fun v2FieldsDecode() {
        val values = IntArray(GameSnapshotLayout.SLOT_COUNT)
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
        values[GameSnapshotLayout.IDX_FRAME_COUNTER] = 5
        values[GameSnapshotLayout.IDX_FLAGS] =
            GameSnapshotLayout.FLAG_SAVE_LOADED or GameSnapshotLayout.FLAG_MENU_OPEN
        values[GameSnapshotLayout.IDX_PAUSE_STATE] = 1

        val snapshot = (decodeSnapshot(values) as SnapshotDecode.Ok).snapshot
        assertTrue(snapshot.saveLoaded)
        assertTrue(snapshot.menuOpen)
        assertTrue(snapshot.isPaused)
    }

    @Test
    fun v2FieldsAbsentDecodeFalse() {
        val values = IntArray(GameSnapshotLayout.SLOT_COUNT)
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
        values[GameSnapshotLayout.IDX_FRAME_COUNTER] = 5

        val snapshot = (decodeSnapshot(values) as SnapshotDecode.Ok).snapshot
        assertFalse(snapshot.saveLoaded)
        assertFalse(snapshot.menuOpen)
        assertFalse(snapshot.isPaused)
    }

    @Test
    fun reportsSchemaMismatchInsteadOfDecodingGarbage() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_SCHEMA_VERSION] = 99
        val result = decodeSnapshot(values)
        assertTrue(result is SnapshotDecode.SchemaMismatch)
        assertEquals(99, (result as SnapshotDecode.SchemaMismatch).nativeVersion)
        assertEquals(GameSnapshotLayout.SCHEMA_VERSION, result.expected)
    }

    @Test
    fun decodesVitals() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_HEALTH] = 48
        values[GameSnapshotLayout.IDX_HEALTH_CAPACITY] = 80
        values[GameSnapshotLayout.IDX_MAGIC] = 24
        values[GameSnapshotLayout.IDX_MAGIC_CAPACITY] = 48
        values[GameSnapshotLayout.IDX_MAGIC_LEVEL] = 1
        values[GameSnapshotLayout.IDX_RUPEES] = 137

        val snapshot = decodeOk(values)

        assertEquals(48, snapshot.health)
        assertEquals(80, snapshot.healthCapacity)
        assertEquals(24, snapshot.magic)
        assertEquals(48, snapshot.magicCapacity)
        assertEquals(1, snapshot.magicLevel)
        assertEquals(137, snapshot.rupees)
    }

    @Test
    fun preservesNegativeValues() {
        // roomNum is s8 and is -1 when invalid; native sign-extends it. If the
        // decoder ever masks instead of sign-extending, -1 becomes 255 and the
        // readout shows a plausible-looking room that does not exist.
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_ROOM_NUM] = -1
        values[GameSnapshotLayout.IDX_HEALTH] = -16

        val snapshot = decodeOk(values)

        assertEquals(-1, snapshot.roomNum)
        assertEquals(-16, snapshot.health)
    }

    @Test
    fun decodesPositionsFromRawFloatBits() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_PLAYER_X] = (-1234.5f).toRawBits()
        values[GameSnapshotLayout.IDX_PLAYER_Y] = 0.0f.toRawBits()
        values[GameSnapshotLayout.IDX_PLAYER_Z] = 987.25f.toRawBits()

        val snapshot = decodeOk(values)

        assertEquals(-1234.5f, snapshot.playerX, 0.0f)
        assertEquals(0.0f, snapshot.playerY, 0.0f)
        assertEquals(987.25f, snapshot.playerZ, 0.0f)
    }

    @Test
    fun decodesFlags() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_FLAGS] =
            GameSnapshotLayout.FLAG_PLAY_STATE_VALID or GameSnapshotLayout.FLAG_IS_NIGHT

        val snapshot = decodeOk(values)

        assertTrue(snapshot.hasPlayState)
        assertFalse(snapshot.hasPlayer)
        assertTrue(snapshot.isNight)
        assertFalse(snapshot.doubleDefense)
    }

    @Test
    fun decodesTheOtherTwoFlagsWhenOnlyThoseAreSet() {
        // The complement of decodesFlags. Without it, bits 1 (PLAYER_VALID) and
        // 3 (DOUBLE_DEFENSE) are only ever asserted false, so swapping them
        // would pass the suite. hasPlayer is what Phase 3's HUD gates world
        // rendering on, so a swap there would not stay cosmetic for long.
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_FLAGS] =
            GameSnapshotLayout.FLAG_PLAYER_VALID or GameSnapshotLayout.FLAG_DOUBLE_DEFENSE

        val snapshot = decodeOk(values)

        assertFalse(snapshot.hasPlayState)
        assertTrue(snapshot.hasPlayer)
        assertFalse(snapshot.isNight)
        assertTrue(snapshot.doubleDefense)
    }

    @Test
    fun decodesButtonItemsAndAmmoInOrder() {
        val values = emptyPayload()
        values[GameSnapshotLayout.IDX_BTN_ITEM_B] = 10
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_LEFT] = 11
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_DOWN] = 12
        values[GameSnapshotLayout.IDX_BTN_ITEM_C_RIGHT] = 13
        values[GameSnapshotLayout.IDX_BTN_AMMO_B] = 20
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_LEFT] = 21
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_DOWN] = 22
        values[GameSnapshotLayout.IDX_BTN_AMMO_C_RIGHT] = 23

        val snapshot = decodeOk(values)

        assertEquals(listOf(10, 11, 12, 13), snapshot.buttonItems)
        assertEquals(listOf(20, 21, 22, 23), snapshot.buttonAmmo)
    }

    @Test
    fun equalSnapshotsCompareEqualSoComposeCanSkipRecomposition() {
        // The whole no-redraw-when-nothing-changed story depends on this. It
        // would silently break if any field were ever changed to an IntArray,
        // which compares by reference.
        val a = decodeOk(emptyPayload())
        val b = decodeOk(emptyPayload())
        assertEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUndersizedArray() {
        decodeSnapshot(IntArray(GameSnapshotLayout.SLOT_COUNT - 1))
    }
}
