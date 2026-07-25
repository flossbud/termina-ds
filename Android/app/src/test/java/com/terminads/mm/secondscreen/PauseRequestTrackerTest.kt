package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseRequestTrackerTest {

    private class Clock(var now: Long = 1_000L)

    private fun snapshot(isPaused: Boolean) = GameSnapshot(
        frameCounter = 7, health = 48, healthCapacity = 48, magic = 0,
        magicCapacity = 0, magicLevel = 0, rupees = 0, playerForm = 4,
        equippedMask = 0, day = 1, timeOfDay = 0x4000, isNight = false,
        doubleDefense = false, buttonItems = listOf(255, 255, 255, 255),
        buttonAmmo = listOf(0, 0, 0, 0), hasPlayState = true,
        hasPlayer = true, sceneId = 0x2D, roomNum = 0,
        playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
        isPaused = isPaused, saveLoaded = true, menuOpen = false,
    )

    @Test
    fun liveExposesTheObservedPauseState() {
        assertEquals(true, BridgeState.Live(snapshot(isPaused = true)).pausedOrNull())
    }

    @Test
    fun stalledExposesTheObservedPauseState() {
        assertEquals(
            true,
            BridgeState.Stalled(
                snapshot(isPaused = true),
                millisSinceChange = 2_000L,
            ).pausedOrNull(),
        )
    }

    @Test
    fun noFramesYetHasNoPauseObservation() {
        assertEquals(null, BridgeState.NoFramesYet.pausedOrNull())
    }

    @Test
    fun nativeUnavailableHasNoPauseObservation() {
        assertEquals(null, BridgeState.NativeUnavailable.pausedOrNull())
    }

    @Test
    fun failedPauseDoesNotRenderAsAResumeFailure() {
        assertEquals(
            false,
            isSubmitFailureVisible(failedTarget = true, screenTarget = false),
        )
    }

    @Test
    fun submitFailureIsVisibleOnlyForItsTarget() {
        assertEquals(
            true,
            isSubmitFailureVisible(failedTarget = true, screenTarget = true),
        )
        assertEquals(
            true,
            isSubmitFailureVisible(failedTarget = false, screenTarget = false),
        )
        assertEquals(
            false,
            isSubmitFailureVisible(failedTarget = null, screenTarget = true),
        )
    }

    @Test
    fun observingTheFailedTargetClearsItsFailure() {
        assertEquals(
            null,
            failedTargetAfterObservation(failedTarget = true, isPaused = true),
        )
        assertEquals(
            true,
            failedTargetAfterObservation(failedTarget = true, isPaused = false),
        )
    }

    @Test
    fun idleUntilRequested() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = false))
    }

    @Test
    fun pendingUntilTheSnapshotAcks() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        assertEquals(PauseRequestState.PENDING, tracker.observe(isPaused = false))
        clock.now += 200
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = true))
    }

    @Test
    fun timesOutVisiblyAndOnce() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        clock.now += 1_001
        assertEquals(PauseRequestState.TIMED_OUT, tracker.observe(isPaused = false))
        // A timeout is reported once, then the tracker returns to idle.
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = false))
    }

    @Test
    fun ackAtTheBoundaryBeatsTheTimeout() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        clock.now += 1_000
        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = true))
    }

    @Test
    fun unacknowledgedRequestTimesOutOnlyAfterTheBoundary() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = true)
        clock.now += 1_000
        assertEquals(PauseRequestState.PENDING, tracker.observe(isPaused = false))
        clock.now += 1
        assertEquals(PauseRequestState.TIMED_OUT, tracker.observe(isPaused = false))
    }

    @Test
    fun pendingRequestSurvivesMissingObservationsAndAcksOnTheNextRealOne() {
        val clock = Clock()
        val tracker = PauseRequestTracker({ clock.now })
        tracker.request(target = false)
        var requestState = tracker.observe(isPaused = true)
        assertEquals(PauseRequestState.PENDING, requestState)

        clock.now += 2_000
        BridgeState.NoFramesYet.pausedOrNull()?.let {
            requestState = tracker.observe(it)
        }
        BridgeState.NativeUnavailable.pausedOrNull()?.let {
            requestState = tracker.observe(it)
        }
        assertEquals(PauseRequestState.PENDING, requestState)

        assertEquals(PauseRequestState.IDLE, tracker.observe(isPaused = false))
    }
}
