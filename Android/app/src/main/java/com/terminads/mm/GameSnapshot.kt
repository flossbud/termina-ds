package com.terminads.mm

/**
 * Layout of the Phase 2 game-state payload.
 *
 * SOURCE OF TRUTH: mm/2s2h/TerminaDS/GameSnapshot.h. These constants are a hand
 * maintained mirror of the enum there. Any change to that header must be
 * reflected here AND must bump SCHEMA_VERSION on both sides -- decodeSnapshot
 * then reports the mismatch instead of decoding garbage.
 *
 * Codegen is not worth it for 39 integers; the runtime guard plus
 * GameSnapshotTest.slotCountMatchesTheDocumentedLayout is the safety net.
 */
object GameSnapshotLayout {
    const val SCHEMA_VERSION = 3

    const val IDX_SCHEMA_VERSION = 0
    const val IDX_FRAME_COUNTER = 1
    const val IDX_FLAGS = 2

    const val IDX_HEALTH = 3
    const val IDX_HEALTH_CAPACITY = 4
    const val IDX_MAGIC = 5
    const val IDX_MAGIC_CAPACITY = 6
    const val IDX_MAGIC_LEVEL = 7
    const val IDX_RUPEES = 8

    const val IDX_PLAYER_FORM = 9
    const val IDX_EQUIPPED_MASK = 10
    const val IDX_DAY = 11
    const val IDX_TIME_OF_DAY = 12

    const val IDX_BTN_ITEM_B = 13
    const val IDX_BTN_ITEM_C_LEFT = 14
    const val IDX_BTN_ITEM_C_DOWN = 15
    const val IDX_BTN_ITEM_C_RIGHT = 16
    const val IDX_BTN_AMMO_B = 17
    const val IDX_BTN_AMMO_C_LEFT = 18
    const val IDX_BTN_AMMO_C_DOWN = 19
    const val IDX_BTN_AMMO_C_RIGHT = 20

    const val IDX_SCENE_ID = 21
    const val IDX_ROOM_NUM = 22
    const val IDX_PLAYER_X = 23
    const val IDX_PLAYER_Y = 24
    const val IDX_PLAYER_Z = 25
    const val IDX_PLAYER_YAW = 26
    const val IDX_PAUSE_STATE = 27

    // v3: the ten graphics settings the Options subscreen renders, plus the
    // live display refresh rate. They ride the snapshot rather than a JNI
    // getter because CVars are an unmutexed map the game thread writes.
    const val IDX_CVAR_INTERNAL_RES = 28
    const val IDX_CVAR_MSAA = 29
    const val IDX_CVAR_FPS = 30
    const val IDX_CVAR_MATCH_HZ = 31
    const val IDX_CVAR_TEXTURE_FILTER = 32
    const val IDX_CVAR_CLOCK_TYPE = 33
    const val IDX_CVAR_BLUR_MODE = 34
    const val IDX_CVAR_BLUR_STRENGTH = 35
    const val IDX_CVAR_DRAW_DISTANCE = 36
    const val IDX_CVAR_3D_ITEM_DROPS = 37
    const val IDX_DISPLAY_REFRESH_HZ = 38

    const val SLOT_COUNT = 39

    const val FLAG_PLAY_STATE_VALID = 1 shl 0
    const val FLAG_PLAYER_VALID = 1 shl 1
    const val FLAG_IS_NIGHT = 1 shl 2
    const val FLAG_DOUBLE_DEFENSE = 1 shl 3
    const val FLAG_SAVE_LOADED = 1 shl 4
    const val FLAG_MENU_OPEN = 1 shl 5
}

/**
 * Engine graphics configuration as of this frame. Mirrors the CVars
 * mm/2s2h/BenGui/BenMenu.cpp binds its own Settings/Enhancements Graphics rows
 * to, so the two menus cannot disagree about what a row means.
 *
 * [internalResPercent] is the float CVar gSettings.InternalResolution scaled by
 * 100, so the payload stays int32 end to end.
 * [displayRefreshHz] is not a CVar -- it is the live display rate, which the
 * FPS row needs for its maximum and its chip.
 */
data class GameSettings(
    val internalResPercent: Int,
    val msaa: Int,
    val fps: Int,
    val matchRefreshRate: Boolean,
    val textureFilter: Int,
    val clockType: Int,
    val motionBlurMode: Int,
    val motionBlurStrength: Int,
    val actorDrawDistance: Int,
    val threeDItemDrops: Boolean,
    val displayRefreshHz: Int,
)

/**
 * One frame of read-only game state.
 *
 * Every field is a plain value copied while the game thread held valid
 * pointers. Nothing here refers to engine memory.
 *
 * buttonItems/buttonAmmo are List, not IntArray, deliberately: this is a data
 * class, and IntArray would make equals() reference-based, defeating Compose's
 * skip-recomposition-when-equal optimisation.
 *
 * When hasPlayState is false the world fields (sceneId, roomNum, position, yaw)
 * are zero rather than stale -- the game thread had no world to read.
 */
data class GameSnapshot(
    val frameCounter: Int,
    val health: Int,
    val healthCapacity: Int,
    val magic: Int,
    val magicCapacity: Int,
    val magicLevel: Int,
    val rupees: Int,
    val playerForm: Int,
    val equippedMask: Int,
    val day: Int,
    val timeOfDay: Int,
    val isNight: Boolean,
    val doubleDefense: Boolean,
    /** B, C-left, C-down, C-right. */
    val buttonItems: List<Int>,
    /** Ammo for the corresponding entry in [buttonItems]; 0 where not applicable. */
    val buttonAmmo: List<Int>,
    val hasPlayState: Boolean,
    val hasPlayer: Boolean,
    val sceneId: Int,
    val roomNum: Int,
    val playerX: Float,
    val playerY: Float,
    val playerZ: Float,
    val playerYaw: Int,
    /** v2: the frame-advance gate holds the Play update frozen (our pause). */
    val isPaused: Boolean,
    /** v2: a save file is active — the honest "is there a game" signal. */
    val saveLoaded: Boolean,
    /** v2: kaleido or the BenMenu owns the game's screen. */
    val menuOpen: Boolean,
    /** v3: engine graphics configuration, for the Options subscreen. */
    val settings: GameSettings,
)

/** Outcome of decoding a raw payload. */
sealed interface SnapshotDecode {
    data class Ok(val snapshot: GameSnapshot) : SnapshotDecode

    /** Native was built from a different layout than this Kotlin mirror. */
    data class SchemaMismatch(val nativeVersion: Int, val expected: Int) : SnapshotDecode
}

/**
 * Decode a raw payload. Pure: no Android dependencies, no native calls.
 *
 * @throws IllegalArgumentException if [values] is smaller than SLOT_COUNT. That
 *   is a programming error -- the poller always allocates exactly SLOT_COUNT --
 *   not a runtime condition to render.
 */
fun decodeSnapshot(values: IntArray): SnapshotDecode {
    require(values.size >= GameSnapshotLayout.SLOT_COUNT) {
        "snapshot payload has ${values.size} slots, need ${GameSnapshotLayout.SLOT_COUNT}"
    }

    val version = values[GameSnapshotLayout.IDX_SCHEMA_VERSION]
    if (version != GameSnapshotLayout.SCHEMA_VERSION) {
        return SnapshotDecode.SchemaMismatch(version, GameSnapshotLayout.SCHEMA_VERSION)
    }

    val flags = values[GameSnapshotLayout.IDX_FLAGS]
    fun flag(bit: Int) = (flags and bit) != 0

    return SnapshotDecode.Ok(
        GameSnapshot(
            frameCounter = values[GameSnapshotLayout.IDX_FRAME_COUNTER],
            health = values[GameSnapshotLayout.IDX_HEALTH],
            healthCapacity = values[GameSnapshotLayout.IDX_HEALTH_CAPACITY],
            magic = values[GameSnapshotLayout.IDX_MAGIC],
            magicCapacity = values[GameSnapshotLayout.IDX_MAGIC_CAPACITY],
            magicLevel = values[GameSnapshotLayout.IDX_MAGIC_LEVEL],
            rupees = values[GameSnapshotLayout.IDX_RUPEES],
            playerForm = values[GameSnapshotLayout.IDX_PLAYER_FORM],
            equippedMask = values[GameSnapshotLayout.IDX_EQUIPPED_MASK],
            day = values[GameSnapshotLayout.IDX_DAY],
            timeOfDay = values[GameSnapshotLayout.IDX_TIME_OF_DAY],
            isNight = flag(GameSnapshotLayout.FLAG_IS_NIGHT),
            doubleDefense = flag(GameSnapshotLayout.FLAG_DOUBLE_DEFENSE),
            buttonItems = listOf(
                values[GameSnapshotLayout.IDX_BTN_ITEM_B],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_LEFT],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_DOWN],
                values[GameSnapshotLayout.IDX_BTN_ITEM_C_RIGHT],
            ),
            buttonAmmo = listOf(
                values[GameSnapshotLayout.IDX_BTN_AMMO_B],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_LEFT],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_DOWN],
                values[GameSnapshotLayout.IDX_BTN_AMMO_C_RIGHT],
            ),
            hasPlayState = flag(GameSnapshotLayout.FLAG_PLAY_STATE_VALID),
            hasPlayer = flag(GameSnapshotLayout.FLAG_PLAYER_VALID),
            sceneId = values[GameSnapshotLayout.IDX_SCENE_ID],
            roomNum = values[GameSnapshotLayout.IDX_ROOM_NUM],
            playerX = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_X]),
            playerY = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_Y]),
            playerZ = Float.fromBits(values[GameSnapshotLayout.IDX_PLAYER_Z]),
            playerYaw = values[GameSnapshotLayout.IDX_PLAYER_YAW],
            isPaused = values[GameSnapshotLayout.IDX_PAUSE_STATE] != 0,
            saveLoaded = flag(GameSnapshotLayout.FLAG_SAVE_LOADED),
            menuOpen = flag(GameSnapshotLayout.FLAG_MENU_OPEN),
            settings = GameSettings(
                internalResPercent = values[GameSnapshotLayout.IDX_CVAR_INTERNAL_RES],
                msaa = values[GameSnapshotLayout.IDX_CVAR_MSAA],
                fps = values[GameSnapshotLayout.IDX_CVAR_FPS],
                matchRefreshRate = values[GameSnapshotLayout.IDX_CVAR_MATCH_HZ] != 0,
                textureFilter = values[GameSnapshotLayout.IDX_CVAR_TEXTURE_FILTER],
                clockType = values[GameSnapshotLayout.IDX_CVAR_CLOCK_TYPE],
                motionBlurMode = values[GameSnapshotLayout.IDX_CVAR_BLUR_MODE],
                motionBlurStrength = values[GameSnapshotLayout.IDX_CVAR_BLUR_STRENGTH],
                actorDrawDistance = values[GameSnapshotLayout.IDX_CVAR_DRAW_DISTANCE],
                threeDItemDrops = values[GameSnapshotLayout.IDX_CVAR_3D_ITEM_DROPS] != 0,
                displayRefreshHz = values[GameSnapshotLayout.IDX_DISPLAY_REFRESH_HZ],
            ),
        )
    )
}
