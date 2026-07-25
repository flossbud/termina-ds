package com.terminads.mm

/** Outcome of submitting one command; mirrors TdsSubmitStatus + UNKNOWN. */
enum class SubmitStatus { OK, FULL, INVALID, UNKNOWN }

/**
 * The only Kotlin writer into the game. Commands are ABSOLUTE -- callers
 * state the target value, never a delta -- because the UI's view of game
 * state is up to ~100 ms stale by construction (spec §3).
 *
 * The native ring is single-producer. Call this bridge only from the Android
 * main thread (Compose handlers); concurrent callers violate that contract.
 *
 * @param submit the native entry, normally NativeBridge::submitCommand;
 *   injected so JVM tests need no native library.
 */
class CommandBridge(private val submit: (Int, Int, Int, String?) -> Int) {

    fun setPaused(paused: Boolean): SubmitStatus =
        decode(submit(OP_PAUSE_SET, if (paused) 1 else 0, 0, null))

    fun setCVarInt(name: String, value: Int): SubmitStatus =
        decode(submit(OP_CVAR_SET_INT, value, 0, name))

    fun saveCVars(): SubmitStatus = decode(submit(OP_CVAR_SAVE, 0, 0, null))

    /**
     * The three settings whose CVar write alone changes nothing: BenMenu
     * applies them through a Callback and the interpreter reads the CVars only
     * at Init. Native performs the write and the apply in one drained command.
     *
     * Values are range-checked natively; an out-of-range value returns INVALID
     * rather than reaching the engine.
     */
    fun setInternalResPercent(percent: Int): SubmitStatus =
        decode(submit(OP_SET_INTERNAL_RES, percent, 0, null))

    fun setMsaa(level: Int): SubmitStatus = decode(submit(OP_SET_MSAA, level, 0, null))

    fun setTextureFilter(mode: Int): SubmitStatus =
        decode(submit(OP_SET_TEXTURE_FILTER, mode, 0, null))

    private fun decode(status: Int): SubmitStatus = when (status) {
        0 -> SubmitStatus.OK
        1 -> SubmitStatus.FULL
        2 -> SubmitStatus.INVALID
        // Anything else means the halves disagree -- permanent, surfaced.
        else -> SubmitStatus.UNKNOWN
    }

    companion object {
        // Mirrors TdsCommandOp in mm/2s2h/TerminaDS/CommandMailbox.h.
        const val OP_PAUSE_SET = 1
        const val OP_CVAR_SET_INT = 2
        const val OP_CVAR_SAVE = 3
        const val OP_SET_INTERNAL_RES = 4
        const val OP_SET_MSAA = 5
        const val OP_SET_TEXTURE_FILTER = 6
    }
}
