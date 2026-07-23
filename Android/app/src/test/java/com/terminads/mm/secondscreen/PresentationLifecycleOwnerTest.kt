package com.terminads.mm.secondscreen

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationLifecycleOwnerTest {

    @Test
    fun startsInInitializedState() {
        val owner = PresentationLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)
    }

    @Test
    fun advancesThroughCreatedStartedResumed() {
        val owner = PresentationLifecycleOwner()

        owner.onCreate()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onStart()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.onResume()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
    }

    @Test
    fun windsBackDownThroughPauseAndStop() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        owner.onPause()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.onStop()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
    }

    @Test
    fun destroyEndsLifecycleAndClearsViewModelStore() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        owner.onStart()
        owner.onResume()

        val store = owner.viewModelStore
        owner.onDestroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(0, store.keys().size)
    }

    @Test
    fun savedStateRegistryIsRestoredAfterCreate() {
        val owner = PresentationLifecycleOwner()
        owner.onCreate()
        assertEquals(true, owner.savedStateRegistry.isRestored)
    }
}
