/*
 * Termina DS: layout contract for the Phase 2 read-only state bridge.
 *
 * The payload is an int32_t[TDS_SNAP_COUNT], not a struct. The enum below IS
 * the contract, which is why there is no separate struct for the marshalling
 * code to drift away from, and why padding and endianness never enter into it.
 *
 * Android/app/src/main/java/com/terminads/mm/GameSnapshot.kt mirrors these
 * indices by hand. BUMP TDS_SNAP_SCHEMA_VERSION ON ANY CHANGE BELOW and bump
 * GameSnapshotLayout.SCHEMA_VERSION to match -- the Kotlin decoder then reports
 * the mismatch rather than decoding garbage.
 */
#ifndef TERMINADS_GAME_SNAPSHOT_H
#define TERMINADS_GAME_SNAPSHOT_H

#include <stdbool.h>
#include <stdint.h>

#define TDS_SNAP_SCHEMA_VERSION 1

enum TdsSnapshotIndex {
    TDS_SNAP_IDX_SCHEMA_VERSION = 0,
    TDS_SNAP_IDX_FRAME_COUNTER,
    TDS_SNAP_IDX_FLAGS,

    TDS_SNAP_IDX_HEALTH,
    TDS_SNAP_IDX_HEALTH_CAPACITY,
    TDS_SNAP_IDX_MAGIC,
    TDS_SNAP_IDX_MAGIC_CAPACITY,
    TDS_SNAP_IDX_MAGIC_LEVEL,
    TDS_SNAP_IDX_RUPEES,

    TDS_SNAP_IDX_PLAYER_FORM,
    TDS_SNAP_IDX_EQUIPPED_MASK,
    TDS_SNAP_IDX_DAY,
    TDS_SNAP_IDX_TIME_OF_DAY,

    TDS_SNAP_IDX_BTN_ITEM_B,
    TDS_SNAP_IDX_BTN_ITEM_C_LEFT,
    TDS_SNAP_IDX_BTN_ITEM_C_DOWN,
    TDS_SNAP_IDX_BTN_ITEM_C_RIGHT,
    TDS_SNAP_IDX_BTN_AMMO_B,
    TDS_SNAP_IDX_BTN_AMMO_C_LEFT,
    TDS_SNAP_IDX_BTN_AMMO_C_DOWN,
    TDS_SNAP_IDX_BTN_AMMO_C_RIGHT,

    TDS_SNAP_IDX_SCENE_ID,
    TDS_SNAP_IDX_ROOM_NUM,
    TDS_SNAP_IDX_PLAYER_X,
    TDS_SNAP_IDX_PLAYER_Y,
    TDS_SNAP_IDX_PLAYER_Z,
    TDS_SNAP_IDX_PLAYER_YAW,

    TDS_SNAP_COUNT
};

enum TdsSnapshotFlag {
    TDS_SNAP_FLAG_PLAY_STATE_VALID = 1 << 0,
    TDS_SNAP_FLAG_PLAYER_VALID = 1 << 1,
    TDS_SNAP_FLAG_IS_NIGHT = 1 << 2,
    TDS_SNAP_FLAG_DOUBLE_DEFENSE = 1 << 3
};

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Copy the most recently published snapshot into `out`. Safe to call from any
 * thread; intended for the Android main thread while the game thread publishes.
 *
 * Returns false if `out` is null, if `count` < TDS_SNAP_COUNT, or if the
 * seqlock retry budget was exhausted (a transient collision -- the caller
 * should keep whatever it had). Never blocks the publishing thread.
 *
 * Before the first publish this succeeds and returns TDS_SNAP_SCHEMA_VERSION in
 * slot 0 with every other slot zero, so TDS_SNAP_IDX_FRAME_COUNTER == 0 means
 * "the publisher has not run yet". The schema version is a property of this
 * binary rather than of any one frame, so it is seeded at load time; that is
 * what keeps "no frames yet" distinguishable from a real version mismatch.
 */
bool TerminaDS_ReadSnapshot(int32_t* out, int count);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_GAME_SNAPSHOT_H */
