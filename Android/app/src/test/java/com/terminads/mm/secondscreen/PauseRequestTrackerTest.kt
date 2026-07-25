package com.terminads.mm.secondscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseRequestTrackerTest {

    private class Clock(var now: Long = 1_000L)

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
}
