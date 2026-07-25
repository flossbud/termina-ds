package com.terminads.mm

/** Outcome of one attempt to read the game-state snapshot. */
enum class SnapshotReadResult {
    /** The array now holds a consistent snapshot. */
    OK,

    /** The native library is not loaded, or the symbol is missing from it. */
    UNAVAILABLE,

    /**
     * A transient seqlock collision -- the game thread was mid-publish for the
     * whole retry budget. Self-healing: keep the previous value and poll again.
     */
    RETRY_EXHAUSTED,

    /**
     * Native refused the array because it is shorter than the native payload,
     * i.e. this build's Kotlin layout mirror is smaller than
     * mm/2s2h/TerminaDS/GameSnapshot.h. Permanent -- every subsequent call fails
     * identically -- and must never be treated as transient.
     */
    BUFFER_TOO_SMALL,

    /**
     * Native returned a status code this build does not know, which means the
     * native half is newer than this Kotlin half. Same family as
     * [BUFFER_TOO_SMALL]: a permanent build skew, not something to retry.
     */
    UNKNOWN_STATUS,
}

/**
 * The only place in the Kotlin layer permitted to call into native code.
 *
 * Declared as a plain object without @JvmStatic, so JNI entry points take
 * (JNIEnv*, jobject) and resolve as Java_com_terminads_mm_NativeBridge_*.
 *
 * The native library is loaded by SDLActivity during MainActivity.onCreate.
 * Calls made before that throw UnsatisfiedLinkError, which we translate to a
 * sentinel rather than crashing the second screen.
 */
object NativeBridge {

    // Hand-maintained mirror of enum TdsSnapshotStatus in
    // mm/2s2h/TerminaDS/GameSnapshot.h, which is the source of truth -- the same
    // arrangement GameSnapshotLayout has with the payload indices.
    private const val STATUS_OK = 0
    private const val STATUS_RETRY_EXHAUSTED = 1
    private const val STATUS_BUFFER_TOO_SMALL = 2

    private external fun nativeGetUptimeMillis(): Long

    private external fun nativeReadSnapshot(out: IntArray): Int

    private external fun nativeSubmitCommand(op: Int, a: Int, b: Int, name: String?): Int

    /** Native process uptime in milliseconds, or -1 if native is not loaded yet. */
    fun uptimeMillis(): Long =
        try {
            nativeGetUptimeMillis()
        } catch (e: UnsatisfiedLinkError) {
            -1L
        }

    /**
     * Fill [out] with the latest published snapshot.
     *
     * [out] must have at least [GameSnapshotLayout.SLOT_COUNT] elements and is
     * expected to be reused across calls -- nothing here allocates.
     */
    fun readSnapshot(out: IntArray): SnapshotReadResult =
        try {
            statusToResult(nativeReadSnapshot(out))
        } catch (e: UnsatisfiedLinkError) {
            SnapshotReadResult.UNAVAILABLE
        }

    /** Submit one absolute game command, or -1 if native is not loaded yet. */
    fun submitCommand(op: Int, a: Int, b: Int, name: String?): Int =
        try {
            nativeSubmitCommand(op, a, b, name)
        } catch (e: UnsatisfiedLinkError) {
            -1
        }

    /**
     * Translate a native TdsSnapshotStatus into a [SnapshotReadResult].
     *
     * Split out from [readSnapshot] because that call cannot run under JVM unit
     * tests (there is no .so), while this mapping -- the part that decides
     * whether a fault is transient or permanent -- can.
     *
     * An unrecognised code is deliberately not folded into RETRY_EXHAUSTED.
     * Doing so would put the poller back in the position this status enum
     * exists to fix: silently retrying a fault that can never clear.
     */
    internal fun statusToResult(status: Int): SnapshotReadResult = when (status) {
        STATUS_OK -> SnapshotReadResult.OK
        STATUS_RETRY_EXHAUSTED -> SnapshotReadResult.RETRY_EXHAUSTED
        STATUS_BUFFER_TOO_SMALL -> SnapshotReadResult.BUFFER_TOO_SMALL
        else -> SnapshotReadResult.UNKNOWN_STATUS
    }
}
