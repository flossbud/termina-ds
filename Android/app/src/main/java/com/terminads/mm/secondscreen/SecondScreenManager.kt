package com.terminads.mm.secondscreen

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.terminads.mm.CommandBridge
import com.terminads.mm.NativeBridge

/**
 * Owns second-screen discovery and Presentation lifecycle.
 *
 * Knows nothing about game state. Its whole contract is start, stop, and which
 * display — everything else lives behind SecondScreenHost and NativeBridge.
 *
 * A missing secondary display is normal, not an error: it is what happens when
 * the device is docked over USB-C or running on single-screen hardware.
 */
class SecondScreenManager(private val activity: Activity) {

    private companion object {
        const val TAG = "TerminaDS/SecondScreen"
        const val PREFS = "termina_ds_second_screen"
        const val KEY_DISPLAY_OVERRIDE = "display_override_id"
    }

    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private val commandBridge = CommandBridge(NativeBridge::submitCommand)
    private val mainDisplayRefreshReporter = MainDisplayRefreshReporter { hz ->
        // refresh() runs synchronously from Activity lifecycle callbacks or
        // DisplayListener callbacks delivered through the main-looper Handler.
        // CommandBridge is therefore always called by its one Android producer.
        commandBridge.setDisplayRefreshHz(hz)
    }

    private var presentation: SecondScreenPresentation? = null
    private var shownInfo: DisplayInfo? = null
    private var started = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        displayManager.registerDisplayListener(displayListener, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(displayListener)
        dismiss()
    }

    fun onActivityResume() {
        if (started) refresh()
    }

    fun onActivityPause() {
        // The Presentation is left up: the second screen stays useful while the
        // activity is briefly not resumed. Teardown happens in stop().
    }

    private fun refresh() {
        if (!started) return

        val displays = displayManager.displays
        val infos = displays.map { it.toDisplayInfo() }
        mainDisplayRefreshReporter.refresh(infos)
        val chosen = DisplaySelectionPolicy.select(infos, readOverride())

        if (chosen == null) {
            if (presentation != null) {
                Log.i(TAG, "No secondary display; dismissing second screen.")
                dismiss()
            }
            return
        }

        // Re-show when the target display changes OR when the same display's
        // properties change (e.g. a refresh-rate / mode switch): compare the full
        // DisplayInfo, not just the id, so the UI reflects the current mode.
        // onDisplayChanged fires for mode changes with the same displayId.
        val current = presentation
        if (current != null && current.isShowing && shownInfo == chosen) {
            return
        }

        dismiss()

        val target = displays.firstOrNull { it.displayId == chosen.displayId } ?: return
        Log.i(TAG, "Showing second screen on display ${chosen.displayId} (${chosen.name}) @ ${chosen.refreshRate}Hz.")

        try {
            presentation = SecondScreenPresentation(activity, target, chosen).also { it.show() }
            shownInfo = chosen
        } catch (e: Exception) {
            // A display can disappear between enumeration and show().
            Log.w(TAG, "Failed to show second screen on display ${chosen.displayId}", e)
            presentation = null
            shownInfo = null
        }
    }

    private fun dismiss() {
        presentation?.let {
            if (it.isShowing) it.dismiss()
        }
        presentation = null
        shownInfo = null
    }

    private fun readOverride(): Int? {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getInt(KEY_DISPLAY_OVERRIDE, -1)
        return if (value >= 0) value else null
    }

    private fun Display.toDisplayInfo(): DisplayInfo {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getMetrics(metrics)
        return DisplayInfo(
            displayId = displayId,
            name = name ?: "display-$displayId",
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            // Display.getRefreshRate() reports an unreliable value for secondary /
            // presentation displays; the active Mode's rate is the source of truth.
            refreshRate = mode.refreshRate,
            isDefault = displayId == Display.DEFAULT_DISPLAY,
        )
    }
}
