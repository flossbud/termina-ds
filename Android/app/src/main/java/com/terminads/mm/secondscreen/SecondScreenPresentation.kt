package com.terminads.mm.secondscreen

import android.app.Presentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.terminads.mm.CommandBridge
import com.terminads.mm.GameSnapshotPoller
import com.terminads.mm.NativeBridge

/**
 * Hosts the Termina DS Compose UI on a secondary display.
 *
 * The window is FLAG_NOT_FOCUSABLE: the game's SDL surface on the primary
 * display must keep input focus at all times. Touch still reaches Compose;
 * only focus is withheld.
 */
class SecondScreenPresentation(
    outerContext: Context,
    display: Display,
    private val displayInfo: DisplayInfo,
) : Presentation(outerContext, display) {

    private val lifecycleOwner = PresentationLifecycleOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // A measured 55px navbar inset shrank the design frame to 95%.
        // An unfocused window cannot hide system bars, so these flags lay the
        // window beneath them; the gesture pill may overlay the bottom edge.
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        )

        lifecycleOwner.onCreate()

        // One poller per Presentation: it owns the reusable payload array and
        // the staleness bookkeeping, both of which must not be shared.
        val poller = GameSnapshotPoller(
            read = NativeBridge::readSnapshot,
            nowMillis = SystemClock::uptimeMillis,
        )
        val commandBridge = CommandBridge(NativeBridge::submitCommand)
        val pauseTracker = PauseRequestTracker(SystemClock::uptimeMillis)

        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    pollBridge = poller::poll,
                    commandBridge = commandBridge,
                    pauseTracker = pauseTracker,
                )
            }
        }

        // ComposeView requires all three owners; a Presentation supplies none.
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        setContentView(composeView)

        if (Build.VERSION.SDK_INT >= 30) {
            window?.setDecorFitsSystemWindows(false)
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
    }

    override fun onStop() {
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        lifecycleOwner.onDestroy()
        super.onStop()
    }
}
