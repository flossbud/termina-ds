package com.terminads.mm.secondscreen

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

        val composeView = ComposeView(context).apply {
            setContent {
                SecondScreenHost(
                    displayInfo = displayInfo,
                    uptimeMillisProvider = { NativeBridge.uptimeMillis() },
                )
            }
        }

        // ComposeView requires all three owners; a Presentation supplies none.
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        setContentView(composeView)
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
