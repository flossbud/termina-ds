package com.terminads.mm.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.terminads.mm.BridgeState
import com.terminads.mm.GameSnapshot
import kotlinx.coroutines.delay

/**
 * Phase 2 debug readout for the Termina DS second screen.
 *
 * Deliberately not designed. It exists to prove the state bridge carries real
 * values, and it is deleted wholesale by Phase 3's HUD.
 *
 * Raw output is the point: a mis-decoded s16 reads as obvious garbage here,
 * whereas a styled heart row would render it as a believable wrong number. Both
 * Thor displays are FLAG_SECURE, so this text is the only way anyone sees what
 * the bridge produced.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    pollBridge: () -> BridgeState,
    pollIntervalMillis: Long = 100L,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var state by remember { mutableStateOf<BridgeState>(BridgeState.NoFramesYet) }

            // Main-thread coroutine scoped to this composition: it starts when
            // the Presentation shows and stops when it dismisses. No thread, no
            // executor, no Handler -- the Presentation lifecycle owner drops the
            // main-thread assertion, so nothing here may leave the main thread.
            LaunchedEffect(pollBridge, pollIntervalMillis) {
                while (true) {
                    state = pollBridge()
                    delay(pollIntervalMillis)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Termina DS", style = MaterialTheme.typography.titleMedium)
                DebugRow("display", "${displayInfo.displayId} ${displayInfo.name}")
                DebugRow("size", "${displayInfo.widthPx}x${displayInfo.heightPx} @ ${displayInfo.refreshRate}Hz")

                val status = statusLine(state)
                Text(
                    text = status,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Bridge status: $status" },
                )

                when (state) {
                    is BridgeState.Live -> SnapshotRows((state as BridgeState.Live).snapshot)
                    is BridgeState.Stalled -> SnapshotRows((state as BridgeState.Stalled).snapshot)
                    else -> Unit
                }
            }
        }
    }
}

private fun statusLine(state: BridgeState): String = when (state) {
    is BridgeState.NativeUnavailable -> "NATIVE NOT LOADED"
    is BridgeState.SchemaMismatch ->
        "SCHEMA MISMATCH native=${state.nativeVersion} expected=${state.expected}"
    is BridgeState.NoFramesYet -> "NO FRAMES YET (publisher has not run)"
    is BridgeState.Stalled -> "STALLED ${state.millisSinceChange}ms  frame=${state.snapshot.frameCounter}"
    is BridgeState.Live -> "LIVE  frame=${state.snapshot.frameCounter}"
}

/**
 * ITEM_NONE widened from the engine's u8 button-item slot: a real, expected
 * value meaning "nothing equipped", not a decode error.
 */
private const val ITEM_NONE = 255

/** Engine `Room.num` is a signed s8; -1 is "no room", a valid transitional state. */
private const val ROOM_NUM_NONE = -1

private fun formatButtonItem(id: Int): String = if (id == ITEM_NONE) "$id(empty)" else "$id"

private fun formatRoomNum(roomNum: Int): String =
    if (roomNum == ROOM_NUM_NONE) "$roomNum(no-room)" else "$roomNum"

@Composable
private fun SnapshotRows(snapshot: GameSnapshot) {
    // Save-derived slots below (health .. btn ammo) are published unconditionally
    // every frame, including on the title screen and file select where no save
    // is loaded -- there is no "save is loaded" flag, only hasPlayState hints at
    // it. Expect these to show something at boot regardless.
    DebugRow("health", "${snapshot.health}/${snapshot.healthCapacity}")
    DebugRow("magic", "${snapshot.magic}/${snapshot.magicCapacity} lvl=${snapshot.magicLevel}")
    DebugRow("rupees", "${snapshot.rupees}")
    DebugRow("form", "${snapshot.playerForm}  mask=${snapshot.equippedMask}")
    DebugRow("clock", "day=${snapshot.day} time=${snapshot.timeOfDay} night=${snapshot.isNight}")
    DebugRow("dbl-def", "${snapshot.doubleDefense}")
    DebugRow("btn items", snapshot.buttonItems.joinToString(" ") { formatButtonItem(it) })
    DebugRow("btn ammo", snapshot.buttonAmmo.joinToString(" "))

    if (snapshot.hasPlayState) {
        // roomNum can legitimately be -1 (ROOM_NUM_NONE) while hasPlayState is
        // true -- that is an expected value during some transitions, not a
        // decode error, so it is annotated rather than treated as broken.
        DebugRow("scene", "${snapshot.sceneId}  room=${formatRoomNum(snapshot.roomNum)}")
    } else {
        DebugRow("scene", "NO WORLD")
    }

    if (snapshot.hasPlayer) {
        DebugRow("pos", "%.1f %.1f %.1f".format(snapshot.playerX, snapshot.playerY, snapshot.playerZ))
        // Raw signed binary angle (engine s16 rotation units), not degrees --
        // the full s16 range, including negatives, is expected here.
        DebugRow("yaw(raw)", "${snapshot.playerYaw}")
    } else {
        DebugRow("pos", "NO PLAYER")
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Text(
        text = label.padEnd(10) + value,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { contentDescription = "$label: $value" },
    )
}
