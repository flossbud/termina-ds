package com.terminads.mm

/** Outcome of one attempt to read the game-state snapshot. */
enum class SnapshotReadResult {
    /** The array now holds a consistent snapshot. */
    OK,

    /** The native library is not loaded, or the symbol is missing from it. */
    UNAVAILABLE,

    /** A transient seqlock collision, or the array was too short. Keep the previous value. */
    RETRY_EXHAUSTED,
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

    private external fun nativeGetUptimeMillis(): Long

    private external fun nativeReadSnapshot(out: IntArray): Boolean

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
            if (nativeReadSnapshot(out)) {
                SnapshotReadResult.OK
            } else {
                SnapshotReadResult.RETRY_EXHAUSTED
            }
        } catch (e: UnsatisfiedLinkError) {
            SnapshotReadResult.UNAVAILABLE
        }
}
