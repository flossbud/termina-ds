/*
 * Termina DS: Phase 4 write path. A fixed-capacity SPSC ring: the Android
 * main thread is the single producer (via JNI), the game thread the single
 * consumer (drained at the head of the OnGameStateUpdate registration,
 * before the snapshot publishes -- the same frame's snapshot reports the
 * effect).
 *
 * Commands are ABSOLUTE ("set paused true"), never read-modify-write: the
 * UI's view of state is up to ~100 ms stale by construction, so a command
 * must mean the same thing regardless of when it lands.
 *
 * Alongside SnapshotPublisher.cpp, CommandMailbox.cpp is the ONLY other
 * file allowed to touch game state.
 */
#ifndef TERMINADS_COMMAND_MAILBOX_H
#define TERMINADS_COMMAND_MAILBOX_H

#include <stdint.h>

#define TDS_CMD_NAME_CAPACITY 64
#define TDS_CMD_QUEUE_CAPACITY 16

enum TdsCommandOp {
    /* a: 0 = resume, nonzero = freeze. Requires a live PlayState. */
    TDS_CMD_PAUSE_SET = 1,
    /* name: CVar key; a: value. For CVars that need no engine apply call. */
    TDS_CMD_CVAR_SET_INT = 2,
    /* Persist CVars via the LUS save path. Debounced by the caller. */
    TDS_CMD_CVAR_SAVE = 3,

    /*
     * The three settings whose CVar write alone changes nothing. BenMenu
     * applies them through .Callback() (BenMenu.cpp:596-599, :621-623) and the
     * interpreter reads the CVars only at Init (interpreter.cpp:4187-4189,
     * Fast3dWindow.cpp:101-102). Each of those three opcodes performs the CVar
     * write AND the engine apply inside one drained command, so a dropped
     * command can never leave a persisted value the screen does not show.
     */
    /* a: 50..200, the internal resolution as a percent. */
    TDS_CMD_SET_INTERNAL_RES = 4,
    /* a: 1..8, the MSAA sample count. */
    TDS_CMD_SET_MSAA = 5,
    /* a: 0..2, FILTER_THREE_POINT / FILTER_LINEAR / FILTER_NONE. */
    TDS_CMD_SET_TEXTURE_FILTER = 6,
    /* a: 1..1000, the Android main display's active refresh rate in Hz. */
    TDS_CMD_SET_DISPLAY_HZ = 7
};

enum TdsSubmitStatus {
    TDS_SUBMIT_OK = 0,
    /* Ring full: the caller surfaces this; it never silently drops. */
    TDS_SUBMIT_FULL = 1,
    /* Unknown op, or a name-carrying op with a null/oversized name. */
    TDS_SUBMIT_INVALID = 2
};

#ifdef __cplusplus
extern "C" {
#endif

/* Producer side. Safe from exactly one non-game thread. */
int32_t TerminaDS_SubmitCommand(int32_t op, int32_t a, int32_t b, const char* name);

/* Consumer side. Game thread only; bounded (drains at most the ring). */
void TerminaDS_DrainCommands(void);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_COMMAND_MAILBOX_H */
