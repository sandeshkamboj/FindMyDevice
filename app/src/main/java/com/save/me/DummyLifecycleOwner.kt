package com.save.me

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class DummyLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    init { registry.currentState = Lifecycle.State.STARTED }
    override fun getLifecycle(): Lifecycle = registry
}