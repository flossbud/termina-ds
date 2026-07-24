package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotPollerTest {

    /** A fake native reader driven entirely from the test. */
    private class FakeReader {
        var result = SnapshotReadResult.OK
        var schemaVersion = GameSnapshotLayout.SCHEMA_VERSION
        var frameCounter = 1
        var health = 48

        fun read(out: IntArray): SnapshotReadResult {
            if (result == SnapshotReadResult.OK) {
                out.fill(0)
                out[GameSnapshotLayout.IDX_SCHEMA_VERSION] = schemaVersion
                out[GameSnapshotLayout.IDX_FRAME_COUNTER] = frameCounter
                out[GameSnapshotLayout.IDX_HEALTH] = health
            }
            return result
        }
    }

    private class FakeClock {
        var now = 1_000L
        fun advance(millis: Long) { now += millis }
    }

    private fun poller(reader: FakeReader, clock: FakeClock) =
        GameSnapshotPoller(
            read = reader::read,
            nowMillis = { clock.now },
            stalenessThresholdMillis = 1_000L,
        )

    @Test
    fun reportsNativeUnavailableWhenTheLibraryIsNotLoaded() {
        val reader = FakeReader().apply { result = SnapshotReadResult.UNAVAILABLE }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NativeUnavailable)
    }

    @Test
    fun reportsSchemaMismatchWithBothVersions() {
        val reader = FakeReader().apply { schemaVersion = 7 }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.SchemaMismatch)
        assertEquals(7, (state as BridgeState.SchemaMismatch).nativeVersion)
        assertEquals(GameSnapshotLayout.SCHEMA_VERSION, state.expected)
    }

    @Test
    fun reportsNoFramesYetWhenThePublisherHasNeverRun() {
        val reader = FakeReader().apply { frameCounter = 0 }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NoFramesYet)
    }

    @Test
    fun reportsLiveWhenTheFrameCounterAdvances() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        assertTrue(subject.poll() is BridgeState.Live)

        reader.frameCounter = 7
        reader.health = 32
        clock.advance(100)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
        assertEquals(32, (state as BridgeState.Live).snapshot.health)
    }

    @Test
    fun staysLiveWhileTheCounterFreezeIsShorterThanTheThreshold() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(500) // under the 1000 ms threshold
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
    }

    @Test
    fun staysLiveAtOneMillisecondUnderTheThreshold() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(999) // just under the 1000 ms threshold
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
    }

    @Test
    fun reportsStalledAtExactlyTheThreshold() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(1_000) // exactly the 1000 ms threshold
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Stalled)
    }

    @Test
    fun reportsStalledWhenTheFrameCounterStopsAdvancing() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(1_500)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Stalled)
        assertEquals(1_500L, (state as BridgeState.Stalled).millisSinceChange)
        // The last good snapshot is retained so the readout still shows values.
        assertEquals(48, state.snapshot.health)
    }

    @Test
    fun recoversFromStalledWhenTheGameResumes() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        clock.advance(1_500)
        assertTrue(subject.poll() is BridgeState.Stalled)

        reader.frameCounter = 2
        clock.advance(100)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
        assertEquals(2, (state as BridgeState.Live).snapshot.frameCounter)
    }

    @Test
    fun keepsThePreviousSnapshotWhenAReadCollides() {
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        subject.poll()
        reader.result = SnapshotReadResult.RETRY_EXHAUSTED
        clock.advance(100)
        val state = subject.poll()

        assertTrue("was $state", state is BridgeState.Live)
        assertEquals(48, (state as BridgeState.Live).snapshot.health)
    }

    @Test
    fun reportsNoFramesYetWhenTheFirstReadEverCollides() {
        val reader = FakeReader().apply { result = SnapshotReadResult.RETRY_EXHAUSTED }
        val state = poller(reader, FakeClock()).poll()
        assertTrue("was $state", state is BridgeState.NoFramesYet)
    }

    @Test
    fun reportsBufferTooSmallDistinctlyAndNeverAsADeadPublisher() {
        // The whole point of the status split. If a future payload grows past
        // this build's SLOT_COUNT, native refuses every call -- and the old
        // boolean read reported that as NoFramesYet, the message reserved for
        // "the publisher never registered". Both Thor displays are FLAG_SECURE,
        // so that line is the only evidence anyone gets.
        val reader = FakeReader().apply { result = SnapshotReadResult.BUFFER_TOO_SMALL }
        val state: BridgeState = poller(reader, FakeClock()).poll()

        assertFalse("must not masquerade as a dead publisher", state is BridgeState.NoFramesYet)
        assertTrue("was $state", state is BridgeState.BufferTooSmall)
        assertEquals(
            GameSnapshotLayout.SLOT_COUNT,
            (state as BridgeState.BufferTooSmall).kotlinSlots,
        )
    }

    @Test
    fun bufferTooSmallIsPermanentAndDoesNotDecayIntoACarriedSnapshot() {
        // It must not behave like RETRY_EXHAUSTED, which keeps the previous
        // snapshot and reports Live. A short buffer can never clear.
        val reader = FakeReader()
        val clock = FakeClock()
        val subject = poller(reader, clock)

        assertTrue(subject.poll() is BridgeState.Live) // a good snapshot now exists

        reader.result = SnapshotReadResult.BUFFER_TOO_SMALL
        clock.advance(100)
        val first = subject.poll()
        clock.advance(100)
        val second = subject.poll()

        assertTrue("was $first", first is BridgeState.BufferTooSmall)
        assertTrue("was $second", second is BridgeState.BufferTooSmall)
    }

    @Test
    fun reportsUnknownStatusDistinctlyAndNeverAsADeadPublisher() {
        // A status code from a native half newer than this Kotlin. Permanent,
        // so it must not be guessed at as a transient collision either.
        val reader = FakeReader().apply { result = SnapshotReadResult.UNKNOWN_STATUS }
        val state: BridgeState = poller(reader, FakeClock()).poll()

        assertFalse("must not masquerade as a dead publisher", state is BridgeState.NoFramesYet)
        assertTrue("was $state", state is BridgeState.UnknownReadStatus)
    }

    @Test
    fun mapsNativeStatusCodesToResultsAndTreatsUnknownOnesAsPermanent() {
        // Mirrors enum TdsSnapshotStatus in mm/2s2h/TerminaDS/GameSnapshot.h.
        // The literals are deliberate: this is the test that fails if the
        // Kotlin mirror drifts from the header's numbering.
        assertEquals(SnapshotReadResult.OK, NativeBridge.statusToResult(0))
        assertEquals(SnapshotReadResult.RETRY_EXHAUSTED, NativeBridge.statusToResult(1))
        assertEquals(SnapshotReadResult.BUFFER_TOO_SMALL, NativeBridge.statusToResult(2))
        assertEquals(SnapshotReadResult.UNKNOWN_STATUS, NativeBridge.statusToResult(3))
        assertEquals(SnapshotReadResult.UNKNOWN_STATUS, NativeBridge.statusToResult(-1))
    }

    @Test
    fun reusesOneArrayAcrossPollsSoNothingAllocatesAtTenHertz() {
        val seen = mutableSetOf<IntArray>()
        val subject = GameSnapshotPoller(
            read = { out ->
                seen.add(out)
                out[GameSnapshotLayout.IDX_SCHEMA_VERSION] = GameSnapshotLayout.SCHEMA_VERSION
                out[GameSnapshotLayout.IDX_FRAME_COUNTER] = 1
                SnapshotReadResult.OK
            },
            nowMillis = { 0L },
        )

        repeat(5) { subject.poll() }

        assertEquals(1, seen.size)
    }
}
