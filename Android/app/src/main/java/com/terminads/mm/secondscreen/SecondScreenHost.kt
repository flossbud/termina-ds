package com.terminads.mm.secondscreen

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.terminads.mm.BridgeState
import com.terminads.mm.CommandBridge
import com.terminads.mm.SubmitStatus
import kotlinx.coroutines.delay

/**
 * The second screen: polls the bridge at 10 Hz on the main thread and routes
 * the result to the gameplay HUD, the idle plate, or the diagnostic plate.
 *
 * The Phase 2 debug readout that used to live here is deleted, as its own
 * charter required; git history keeps it, and the diagnostic plate preserves
 * its exact fault strings.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    pollBridge: () -> BridgeState,
    commandBridge: CommandBridge,
    pauseTracker: PauseRequestTracker,
    pollIntervalMillis: Long = 100L,
) {
    var state by remember { mutableStateOf<BridgeState>(BridgeState.NoFramesYet) }
    var pauseRequestState by remember { mutableStateOf(PauseRequestState.IDLE) }
    var failedTarget by remember { mutableStateOf<Boolean?>(null) }
    var nav by remember { mutableStateOf(PauseNavState()) }
    var previousPaused by remember { mutableStateOf<Boolean?>(null) }
    val saveDebouncer = remember { CVarSaveDebouncer() }

    // Main-thread coroutine scoped to this composition: starts when the
    // Presentation shows, stops when it dismisses. The Presentation lifecycle
    // owner drops the main-thread assertion, so nothing here may leave the
    // main thread.
    LaunchedEffect(pollBridge, pollIntervalMillis) {
        while (true) {
            state = pollBridge()
            // The poll loop is the single clock for the save debounce, so no
            // second timer touches the main thread.
            if (saveDebouncer.isDue(SystemClock.uptimeMillis())) {
                when (commandBridge.saveCVars()) {
                    // Accepted by the ring. Persistence is the game thread's
                    // problem now.
                    SubmitStatus.OK -> saveDebouncer.clear()
                    // Ring momentarily full. Stay pending and retry on the next
                    // poll (~100 ms) -- the game thread drains every frame, so
                    // this clears itself. CVAR_SAVE has no snapshot signal, so
                    // dropping it here would lose the write with no way to
                    // notice.
                    SubmitStatus.FULL -> Unit
                    // INVALID/UNKNOWN are permanent -- a build skew between the
                    // Kotlin and native opcode tables. Retrying cannot help and
                    // would spin every poll forever, so discharge it.
                    SubmitStatus.INVALID, SubmitStatus.UNKNOWN -> saveDebouncer.clear()
                }
            }
            state.pausedOrNull()?.let { paused ->
                if (previousPaused == true && !paused) {
                    nav = PauseNavState()
                }
                previousPaused = paused
                pauseRequestState = pauseTracker.observe(paused)
                failedTarget = failedTargetAfterObservation(failedTarget, paused)
            }
            delay(pollIntervalMillis)
        }
    }

    DesignRoot {
        when (val screen = route(state)) {
            is ScreenKind.Gameplay -> GameplayScreen(
                model = screen.model,
                stalledSeconds = screen.stalledSeconds,
                pauseAvailable = screen.pauseAvailable,
                pausePending = pauseRequestState == PauseRequestState.PENDING,
                pauseFailed =
                    pauseRequestState == PauseRequestState.TIMED_OUT ||
                        isSubmitFailureVisible(failedTarget, screenTarget = true),
                onPauseTap = {
                    when (commandBridge.setPaused(true)) {
                        SubmitStatus.OK -> {
                            failedTarget = null
                            pauseTracker.request(target = true)
                            pauseRequestState = PauseRequestState.PENDING
                        }
                        else -> failedTarget = true
                    }
                },
            )
            is ScreenKind.PauseMenu -> when (nav.view) {
                PauseView.ROOT -> PauseMenuScreen(
                    model = screen.model,
                    resumePending = pauseRequestState == PauseRequestState.PENDING,
                    resumeFailed =
                        pauseRequestState == PauseRequestState.TIMED_OUT ||
                            isSubmitFailureVisible(failedTarget, screenTarget = false),
                    onResumeTap = {
                        when (commandBridge.setPaused(false)) {
                            SubmitStatus.OK -> {
                                failedTarget = null
                                pauseTracker.request(target = false)
                                pauseRequestState = PauseRequestState.PENDING
                            }
                            else -> failedTarget = false
                        }
                    },
                    onOptionsTap = { nav = nav.openOptions() },
                )
                PauseView.OPTIONS -> OptionsScreen(
                    model = screen.model,
                    settings = screen.settings,
                    tab = nav.tab,
                    category = nav.category,
                    selectedKey = nav.selectedKey,
                    onTabSelect = { nav = nav.selectTab(it) },
                    onCategorySelect = { nav = nav.selectCategory(it) },
                    onRowSelect = { nav = nav.selectRow(it) },
                    onRowChange = { key, value ->
                        // Fire and observe: the next snapshot is the ack. A
                        // dropped command simply never changes the row.
                        submitOptionChange(commandBridge, key, value)
                        saveDebouncer.noteChange(SystemClock.uptimeMillis())
                    },
                    onBack = { nav = nav.back() },
                    onResume = {
                        when (commandBridge.setPaused(false)) {
                            SubmitStatus.OK -> {
                                failedTarget = null
                                pauseTracker.request(target = false)
                                pauseRequestState = PauseRequestState.PENDING
                            }
                            else -> failedTarget = false
                        }
                    },
                )
            }
            is ScreenKind.Idle -> IdlePlate(screen.waitingForGame)
            is ScreenKind.Diagnostic -> DiagnosticPlate(screen.message, displayInfo)
        }
    }
}

/**
 * Spec §6 delta: the no-save state the handoff doesn't cover (title screen,
 * file select, game still booting). Quiet plate in the design vocabulary.
 */
@Composable
private fun IdlePlate(waitingForGame: Boolean) {
    val description = if (waitingForGame) "Termina DS, waiting for the game" else "Termina DS"
    Column(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "TERMINA DS",
            style = TerminaType.IdleWordmark
                .toStyle(TerminaColors.Ink3)
                .copy(letterSpacing = 0.18.em),
        )
        BreathingDiamond(Modifier.padding(top = du(22f * LEGIBILITY)))
        if (waitingForGame) {
            Text(
                "WAITING FOR THE GAME",
                style = TerminaType.IdleCaption.toStyle(TerminaColors.TextHint),
                modifier = Modifier.padding(top = du(18f * LEGIBILITY)),
            )
        }
    }
}

/**
 * Build-skew faults stay loud (spec §4): the exact Phase 2 strings, centered,
 * with the display identity beneath -- on a FLAG_SECURE screen this text is
 * the only witness.
 */
@Composable
private fun DiagnosticPlate(message: String, displayInfo: DisplayInfo) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(du(44f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = TerminaType.Diagnostic.toStyle(TerminaColors.GoldDim),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Bridge fault: $message" },
        )
        Text(
            "display ${displayInfo.displayId} ${displayInfo.name} " +
                "${displayInfo.widthPx}x${displayInfo.heightPx}",
            style = TerminaType.IdleCaption.toStyle(TerminaColors.TextDimmest),
            modifier = Modifier.padding(top = du(18f * LEGIBILITY)),
        )
    }
}
