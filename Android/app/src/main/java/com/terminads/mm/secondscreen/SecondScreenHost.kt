package com.terminads.mm.secondscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phase 1 placeholder for the Termina DS second screen.
 *
 * Shows the display Termina DS selected, a native uptime heartbeat proving the
 * JNI seam is live, and a tap counter proving touch input arrives here without
 * disturbing the game on the primary display.
 *
 * Content is intentionally throwaway. Real features begin in Phase 3.
 */
@Composable
fun SecondScreenHost(
    displayInfo: DisplayInfo,
    uptimeMillisProvider: () -> Long,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var uptime by remember { mutableLongStateOf(-1L) }
            var taps by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while (true) {
                    uptime = uptimeMillisProvider()
                    delay(500L)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Termina DS", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Phase 1 second-screen shell",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text("Display", style = MaterialTheme.typography.titleMedium)
                Text("id: ${displayInfo.displayId}")
                Text("name: ${displayInfo.name}")
                Text("size: ${displayInfo.widthPx} x ${displayInfo.heightPx}")
                Text("refresh: ${displayInfo.refreshRate} Hz")

                Text("Native bridge", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (uptime < 0) "not loaded" else "uptime: ${uptime} ms",
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (uptime < 0) {
                                "Native bridge not loaded"
                            } else {
                                "Native uptime $uptime milliseconds"
                            }
                    },
                )

                Text("Touch", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { taps++ }) {
                    Text("Tap me")
                }
                Text("taps: $taps")
            }
        }
    }
}
