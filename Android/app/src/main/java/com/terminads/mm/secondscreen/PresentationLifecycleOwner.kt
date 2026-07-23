package com.terminads.mm.secondscreen

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Supplies the three owners a [android.app.Presentation]'s decor view lacks.
 *
 * A Presentation is a Dialog, so its window has no LifecycleOwner,
 * ViewModelStoreOwner, or SavedStateRegistryOwner. ComposeView requires all
 * three and throws at attach time without them.
 *
 * Driven by the Presentation's own show/dismiss, NOT the host Activity's
 * lifecycle — the second screen can be torn down while the game keeps running.
 */
class PresentationLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // createUnsafe (not the public constructor) is used deliberately: the public
    // LifecycleRegistry(this) constructor asserts every call happens on the main
    // thread, which throws a NullPointerException in plain JVM unit tests (no
    // Robolectric) because Looper.getMainLooper() is stubbed to null. createUnsafe
    // disables only that assertion; state/observer semantics are unchanged.
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
