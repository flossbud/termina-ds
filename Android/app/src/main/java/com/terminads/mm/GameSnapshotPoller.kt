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
    // Reused across polls: nothing allocates on the main thread at 10 Hz.
    private val buffer = IntArray(GameSnapshotLayout.SLOT_COUNT)

    private var lastSnapshot: GameSnapshot? = null
    private var lastFrameCounter = Int.MIN_VALUE
    private var lastChangeMillis = 0L

    fun poll(): BridgeState {
        when (read(buffer)) {
            SnapshotReadResult.UNAVAILABLE -> return BridgeState.NativeUnavailable
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

        val sinceChange = now - lastChangeMillis
        return if (sinceChange >= stalenessThresholdMillis) {
            BridgeState.Stalled(snapshot, sinceChange)
        } else {
            BridgeState.Live(snapshot)
        }
    }

    /**
     * A read collided with the publisher. That is transient by nature, so keep
     * the previous snapshot and let the staleness check catch it if it somehow
     * persists.
     */
    private fun carryForward(): BridgeState {
        val previous = lastSnapshot ?: return BridgeState.NoFramesYet
        val sinceChange = nowMillis() - lastChangeMillis
        return if (sinceChange >= stalenessThresholdMillis) {
            BridgeState.Stalled(previous, sinceChange)
        } else {
            BridgeState.Live(previous)
        }
    }
}
