package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState

enum class PauseRequestState { IDLE, PENDING, TIMED_OUT }

/** Returns a real pause observation, or null when this bridge state has none. */
fun BridgeState.pausedOrNull(): Boolean? = when (this) {
    is BridgeState.Live -> snapshot.isPaused
    is BridgeState.Stalled -> snapshot.isPaused
    else -> null
}

/** A failed submission is visible only on the screen for that target. */
fun isSubmitFailureVisible(failedTarget: Boolean?, screenTarget: Boolean): Boolean =
    failedTarget == screenTarget

/** A real observation of the failed target supersedes the submit failure. */
fun failedTargetAfterObservation(failedTarget: Boolean?, isPaused: Boolean): Boolean? =
    if (failedTarget == isPaused) null else failedTarget

/**
 * Tracks one in-flight pause/resume request against the observed snapshot.
 * The UI never assumes a command took effect: it stays PENDING until the
 * snapshot's isPaused matches the requested target, and a request that
 * never acks becomes one visible TIMED_OUT observation (spec §4) before
 * returning to idle.
 */
class PauseRequestTracker(
    private val nowMillis: () -> Long,
    private val timeoutMillis: Long = 1_000L,
) {
    private var target: Boolean? = null
    private var requestedAt = 0L

    fun request(target: Boolean) {
        this.target = target
        requestedAt = nowMillis()
    }

    fun observe(isPaused: Boolean): PauseRequestState {
        val wanted = target ?: return PauseRequestState.IDLE
        if (isPaused == wanted) {
            target = null
            return PauseRequestState.IDLE
        }
        if (nowMillis() - requestedAt > timeoutMillis) {
            target = null
            return PauseRequestState.TIMED_OUT
        }
        return PauseRequestState.PENDING
    }
}
