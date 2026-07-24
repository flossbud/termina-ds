package com.terminads.mm

/**
 * What the bridge can tell us right now.
 *
 * These states exist because the bottom screen cannot be screenshotted -- both
 * Thor displays are FLAG_SECURE -- so "the numbers are frozen" has to be
 * diagnosable from the numbers themselves.
 */
sealed interface BridgeState {
    /** The native library is not loaded, or the symbol is missing. */
    data object NativeUnavailable : BridgeState

    /** Native was built from a different payload layout than this build's Kotlin. */
    data class SchemaMismatch(val nativeVersion: Int, val expected: Int) : BridgeState

    /** Native is answering, but the publisher has never run. */
    data object NoFramesYet : BridgeState

    /**
     * Native refused the read outright: its payload has more slots than this
     * build's Kotlin mirror allocates.
     *
     * Permanent, and deliberately distinct from [NoFramesYet]. The documented
     * way to extend the payload is to add slots to
     * mm/2s2h/TerminaDS/GameSnapshot.h, so "native grew and Kotlin did not" is
     * the likeliest future fault here -- and while the underlying read returned
     * a bare boolean it rendered as [NoFramesYet], i.e. as a dead publisher, on
     * a screen nobody can screenshot to check.
     *
     * @param kotlinSlots slots this build asked native to fill; native needs more.
     */
    data class BufferTooSmall(val kotlinSlots: Int) : BridgeState

    /**
     * Native returned a status code this build does not recognise, so the
     * native half is newer than the Kotlin half. Permanent, like
     * [BufferTooSmall], and reported separately rather than guessed at.
     */
    data object UnknownReadStatus : BridgeState

    /** The game loop is stepping and the snapshot is current. */
    data class Live(val snapshot: GameSnapshot) : BridgeState

    /** Native is answering but the frame counter has stopped advancing. */
    data class Stalled(val snapshot: GameSnapshot, val millisSinceChange: Long) : BridgeState
}

/**
 * Polls the native snapshot and classifies the result.
 *
 * Call [poll] from the Android main thread only. It never blocks: the native
 * read is a bounded-retry seqlock copy, and a failed read simply keeps the
 * previous snapshot.
 *
 * @param read the native reader, normally `NativeBridge::readSnapshot`
 * @param nowMillis a monotonic clock, normally `SystemClock::uptimeMillis`
 * @param stalenessThresholdMillis how long the frame counter may sit unchanged
 *   before the game loop is considered stopped. At a 10 Hz poll against a 60 Hz
 *   publisher the counter advances every poll, so a full second of no movement
 *   is unambiguous.
 */
class GameSnapshotPoller(
    private val read: (IntArray) -> SnapshotReadResult,
    private val nowMillis: () -> Long,
    private val stalenessThresholdMillis: Long = 1_000L,
) {
    // The raw payload buffer is reused across polls; the decoded GameSnapshot
    // below is not -- decodeSnapshot allocates a new model (and two lists) on
    // every successful poll.
    private val buffer = IntArray(GameSnapshotLayout.SLOT_COUNT)

    private var lastSnapshot: GameSnapshot? = null
    private var lastFrameCounter = Int.MIN_VALUE
    private var lastChangeMillis = 0L

    fun poll(): BridgeState {
        when (read(buffer)) {
            SnapshotReadResult.UNAVAILABLE -> return BridgeState.NativeUnavailable
            // Permanent faults: reported every poll, and never routed through
            // carryForward(), whose no-previous-snapshot answer is NoFramesYet.
            // That would report a layout mismatch as a dead publisher.
            SnapshotReadResult.BUFFER_TOO_SMALL ->
                return BridgeState.BufferTooSmall(GameSnapshotLayout.SLOT_COUNT)
            SnapshotReadResult.UNKNOWN_STATUS -> return BridgeState.UnknownReadStatus
            // Transient: the game thread was mid-publish, so keep what we had.
            SnapshotReadResult.RETRY_EXHAUSTED -> return carryForward()
            SnapshotReadResult.OK -> Unit
        }

        val snapshot = when (val decoded = decodeSnapshot(buffer)) {
            is SnapshotDecode.SchemaMismatch ->
                return BridgeState.SchemaMismatch(decoded.nativeVersion, decoded.expected)
            is SnapshotDecode.Ok -> decoded.snapshot
        }

        if (snapshot.frameCounter == 0) {
            return BridgeState.NoFramesYet
        }

        val now = nowMillis()
        if (snapshot.frameCounter != lastFrameCounter) {
            lastFrameCounter = snapshot.frameCounter
            lastChangeMillis = now
        }
        lastSnapshot = snapshot

        return classify(snapshot, now)
    }

    /**
     * A read collided with the publisher. That is transient by nature, so keep
     * the previous snapshot and let the staleness check catch it if it somehow
     * persists.
     */
    private fun carryForward(): BridgeState {
        val previous = lastSnapshot ?: return BridgeState.NoFramesYet
        return classify(previous, nowMillis())
    }

    /** Staleness classification shared by [poll] and [carryForward]. */
    private fun classify(snapshot: GameSnapshot, now: Long): BridgeState {
        val sinceChange = now - lastChangeMillis
        return if (sinceChange >= stalenessThresholdMillis) {
            BridgeState.Stalled(snapshot, sinceChange)
        } else {
            BridgeState.Live(snapshot)
        }
    }
}
