package com.terminads.mm.secondscreen

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

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

    private var presentation: SecondScreenPresentation? = null
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
        val chosen = DisplaySelectionPolicy.select(infos, readOverride())

        if (chosen == null) {
            if (presentation != null) {
                Log.i(TAG, "No secondary display; dismissing second screen.")
                dismiss()
            }
            return
        }

        val current = presentation
        if (current != null && current.display?.displayId == chosen.displayId && current.isShowing) {
            return
        }

        dismiss()

        val target = displays.firstOrNull { it.displayId == chosen.displayId } ?: return
        Log.i(TAG, "Showing second screen on display ${chosen.displayId} (${chosen.name}).")

        try {
            presentation = SecondScreenPresentation(activity, target, chosen).also { it.show() }
        } catch (e: Exception) {
            // A display can disappear between enumeration and show().
            Log.w(TAG, "Failed to show second screen on display ${chosen.displayId}", e)
            presentation = null
        }
    }

    private fun dismiss() {
        presentation?.let {
            if (it.isShowing) it.dismiss()
        }
        presentation = null
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
            refreshRate = refreshRate,
            isDefault = displayId == Display.DEFAULT_DISPLAY,
        )
    }
}
