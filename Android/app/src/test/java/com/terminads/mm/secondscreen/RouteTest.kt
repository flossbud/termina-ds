package com.terminads.mm.secondscreen

import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private fun snapshot(hasPlayState: Boolean, sceneId: Int = 0x2D) = GameSnapshot(
        frameCounter = 7, health = 48, healthCapacity = 48, magic = 0,
        magicCapacity = 0, magicLevel = 0, rupees = 0, playerForm = 4,
        equippedMask = 0, day = 1, timeOfDay = 0x4000, isNight = false,
        doubleDefense = false, buttonItems = listOf(255, 255, 255, 255),
        buttonAmmo = listOf(0, 0, 0, 0), hasPlayState = hasPlayState,
        hasPlayer = hasPlayState, sceneId = sceneId, roomNum = 0,
        playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
    )

    @Test
    fun preSaveCutsceneSceneIdlesDespiteLiveWorld() {
        // The intro plays in scene 0x08 ("Cutscene Scene") with a live
        // PlayState before any save exists -- never a HUD there.
        assertEquals(
            ScreenKind.Idle(waitingForGame = false),
            route(BridgeState.Live(snapshot(hasPlayState = true, sceneId = 8))),
        )
        assertEquals(
            ScreenKind.Idle(waitingForGame = false),
            route(BridgeState.Stalled(snapshot(hasPlayState = true, sceneId = 8), 2400)),
        )
    }

    @Test
    fun liveWithWorldShowsGameplay() {
        val screen = route(BridgeState.Live(snapshot(hasPlayState = true)))
        assertTrue(screen is ScreenKind.Gameplay)
        assertEquals(null, (screen as ScreenKind.Gameplay).stalledSeconds)
    }

    @Test
    fun liveWithoutWorldIdles() {
        // Title screen / file select: save slots are meaningless, never a HUD.
        assertEquals(ScreenKind.Idle(waitingForGame = false), route(BridgeState.Live(snapshot(false))))
    }

    @Test
    fun stalledWithWorldKeepsTheHudAndReportsSeconds() {
        val screen = route(BridgeState.Stalled(snapshot(true), millisSinceChange = 2400))
        assertEquals(2L, (screen as ScreenKind.Gameplay).stalledSeconds)
    }

    @Test
    fun stalledWithoutWorldIdles() {
        assertEquals(ScreenKind.Idle(waitingForGame = false), route(BridgeState.Stalled(snapshot(false), 2400)))
    }

    @Test
    fun noFramesYetIsTheWaitingIdle() {
        assertEquals(ScreenKind.Idle(waitingForGame = true), route(BridgeState.NoFramesYet))
    }

    @Test
    fun faultsKeepThePhase2DiagnosticStrings() {
        // docs/HANDOFF.md's diagnostic vocabulary must still match the screen.
        assertEquals(
            ScreenKind.Diagnostic("NATIVE NOT LOADED"),
            route(BridgeState.NativeUnavailable),
        )
        assertEquals(
            ScreenKind.Diagnostic("SCHEMA MISMATCH native=2 expected=1"),
            route(BridgeState.SchemaMismatch(nativeVersion = 2, expected = 1)),
        )
        assertEquals(
            ScreenKind.Diagnostic(
                "BUFFER TOO SMALL kotlin=27 slots < native payload " +
                    "(GameSnapshotLayout must mirror GameSnapshot.h)"
            ),
            route(BridgeState.BufferTooSmall(kotlinSlots = 27)),
        )
        assertEquals(
            ScreenKind.Diagnostic("UNKNOWN READ STATUS (native is newer than this build's Kotlin)"),
            route(BridgeState.UnknownReadStatus),
        )
    }
}
