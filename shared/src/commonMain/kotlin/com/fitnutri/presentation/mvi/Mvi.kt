package com.fitnutri.presentation.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal MVI / unidirectional-data-flow core, shared across Android + iOS.
 *
 *   Intent  -> user/UI action sent into the store
 *   State   -> immutable snapshot the UI renders (single source of truth)
 *   Effect  -> one-shot side effect (navigation, toast) the UI consumes once
 *
 * A concrete feature implements [reduce] (pure State x Intent -> State) and
 * optionally [onIntent] for async work that dispatches further intents.
 */

/** Marker for a UI-bound, immutable view state. */
interface MviState

/** Marker for an action flowing into a [Store]. */
interface MviIntent

/** Marker for a one-shot side effect flowing out of a [Store]. */
interface MviEffect

abstract class Store<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    protected val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = 8)
    val effects: Flow<E> = _effects.asSharedFlow()

    /** Current state snapshot. */
    protected val current: S get() = _state.value

    /** Entry point for the UI. Reduces synchronously, then runs async handling. */
    fun dispatch(intent: I) {
        _state.value = reduce(current, intent)
        scope.launch { onIntent(intent) }
    }

    /** Pure reducer: derive the next state from the current state and an intent. */
    protected abstract fun reduce(state: S, intent: I): S

    /** Async side-effect handling (network, repos). Default: no-op. */
    protected open suspend fun onIntent(intent: I) {}

    /** Update state from inside async handlers. */
    protected fun setState(reducer: (S) -> S) {
        _state.value = reducer(current)
    }

    /** Emit a one-shot effect to the UI. */
    protected suspend fun emitEffect(effect: E) {
        _effects.emit(effect)
    }
}
