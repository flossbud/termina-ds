package com.terminads.mm.secondscreen

import android.app.Presentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

        lifecycleOwner.onCreate()

        // One poller per Presentation: it owns the reusable payload array and
        // the staleness bookkeeping, both of which must not be shared.
        val poller = GameSnapshotPoller(
            read = NativeBridge::readSnapshot,
            nowMillis = SystemClock::uptimeMillis,
        )

        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    pollBridge = poller::poll,
                )
            }
        }

        // ComposeView requires all three owners; a Presentation supplies none.
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        setContentView(composeView)

        if (Build.VERSION.SDK_INT >= 30) {
            // The Thor reserved a measured 55px navbar strip here, shrinking
            // the 1240x1080 design frame to 95% in the 1240x1025 app window.
            window?.setDecorFitsSystemWindows(false)
            window?.insetsController?.apply {
                hide(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars(),
                )
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
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
