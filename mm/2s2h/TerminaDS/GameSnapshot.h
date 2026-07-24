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

    // Everything from here through TDS_SNAP_IDX_BTN_AMMO_C_RIGHT is read out of
    // gSaveContext unconditionally, every frame -- including on the title
    // screen and at file select, where gSaveContext holds no meaningful save.
    // There is no flag for "a save is loaded"; the only signal a consumer has
    // is TDS_SNAP_FLAG_PLAY_STATE_VALID, which these slots do not depend on
    // and can be set or clear independently of them. Do not render this range
    // as a real HUD unless PLAY_STATE_VALID (and, ideally, PLAYER_VALID) is
    // also set, or boot/menu screens will show a plausible-looking but
    // meaningless health/magic/rupee readout.
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

/*
 * Status codes returned by TerminaDS_ReadSnapshot and, unchanged, by the JNI
 * entry point Java_com_terminads_mm_NativeBridge_nativeReadSnapshot. This enum
 * is the second half of the seam's contract, alongside the layout enum above:
 * NativeBridge.kt mirrors these three values by hand, exactly as
 * GameSnapshot.kt mirrors the indices.
 *
 * The read used to return a bare bool, which collapsed two unrelated outcomes
 * into one. RETRY_EXHAUSTED is transient and self-healing -- the caller keeps
 * whatever it had and tries again in 100 ms. BUFFER_TOO_SMALL is neither: it
 * means the caller's array is shorter than TDS_SNAP_COUNT, so it fails on every
 * call forever and no amount of retrying fixes it. Reported as one value the
 * caller had to guess, and guessing "transient" turned the documented way of
 * extending this payload -- add slots, bump the schema version -- into a
 * permanent "publisher has not run" on a screen that cannot be screenshotted.
 *
 * Add codes at the end. A caller that does not recognise a code must treat it
 * as a permanent fault, never as OK and never as a transient retry.
 */
enum TdsSnapshotStatus {
    /* `out` now holds a consistent snapshot. */
    TDS_SNAP_STATUS_OK = 0,

    /* Transient seqlock collision: the caller should keep its previous copy. */
    TDS_SNAP_STATUS_RETRY_EXHAUSTED = 1,

    /* `out` is null or shorter than TDS_SNAP_COUNT. Permanent, a build skew. */
    TDS_SNAP_STATUS_BUFFER_TOO_SMALL = 2
};

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Copy the most recently published snapshot into `out`. Safe to call from any
 * thread; intended for the Android main thread while the game thread publishes.
 *
 * Returns a TdsSnapshotStatus: TDS_SNAP_STATUS_BUFFER_TOO_SMALL if `out` is
 * null or `count` < TDS_SNAP_COUNT, TDS_SNAP_STATUS_RETRY_EXHAUSTED if the
 * seqlock retry budget ran out, TDS_SNAP_STATUS_OK otherwise. Never blocks the
 * publishing thread.
 *
 * On any non-OK status `out` may already have been partially overwritten with a
 * torn read (the seqlock writes optimistically before validating the sequence
 * number). The contents of `out` are unspecified in that case and must not be
 * used -- callers must check the status before consuming `out`, not just before
 * deciding whether to keep a previous copy.
 *
 * Before the first publish this returns TDS_SNAP_STATUS_OK with
 * TDS_SNAP_SCHEMA_VERSION in slot 0 and every other slot zero, so
 * TDS_SNAP_IDX_FRAME_COUNTER == 0 means
 * "the publisher has not run yet". The schema version is a property of this
 * binary rather than of any one frame, so it is seeded at load time; that is
 * what keeps "no frames yet" distinguishable from a real version mismatch.
 */
int32_t TerminaDS_ReadSnapshot(int32_t* out, int count);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_GAME_SNAPSHOT_H */
