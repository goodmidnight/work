package io.goodmidnight.transfer.ui.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<STATE : BaseState, EVENT : BaseEvent, EFFECT : BaseEffect, ERROR : BaseError>(
    initialState: STATE,
) : ViewModel() {

    /**
     * [state]
     * - The single source of truth for the UI state.
     * - Observers can collect this flow to reactively update the UI.
     * - Use [updateState] to safely and atomically mutate the state.
     */
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<STATE> = _state.asStateFlow()

    /**
     * [error]
     * - A stream dedicated to propagating application errors to the UI.
     * - Emits errors via [emitError].
     * - Drops the oldest error if the buffer overflows.
     */
    private val _error: MutableSharedFlow<ERROR> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    protected val error: SharedFlow<ERROR> = _error.asSharedFlow()

    /**
     * [event]
     * - A channel used to receive user intents or system events from the View.
     * - Events are submitted via the [onEvent] function.
     * - Must be bound using [bindEvent] to process incoming events.
     */
    private val _event: Channel<EVENT> = Channel(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    protected val event: Flow<EVENT> = _event.receiveAsFlow()

    /**
     * [effect]
     * - A stream for one-off side effects (e.g., navigation, showing a toast) intended for the UI.
     * - Configured to hold emitted effects in a buffer until a subscriber (the View) is ready to collect them, preventing loss during configuration changes.
     */
    private val _effect: MutableSharedFlow<EFFECT> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 64, // Sufficient buffer to hold effects until the UI subscribes
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    protected val effect: SharedFlow<EFFECT> = _effect.asSharedFlow()

    /**
     * [updateState]
     * - Atomically updates the current [STATE].
     * - Thread-safe modification using the StateFlow `update` block.
     */
    protected fun updateState(action: STATE.() -> STATE) = _state.update(action)

    /**
     * [emitError]
     * - Emits a new [ERROR] to the error flow.
     */
    protected val emitError: suspend (ERROR) -> Unit = { e -> _error.emit(e) }

    /**
     * [onEvent]
     * - Pushes a new [EVENT] from the UI into the event channel to be processed by the ViewModel.
     */
    val onEvent: (EVENT) -> Unit = { e -> viewModelScope.launch { _event.send(e) } }

    /**
     * [emitEffect]
     * - Emits a new [EFFECT] to the effect flow to trigger a one-off UI action.
     */
    protected val emitEffect: suspend (EFFECT) -> Unit = { e -> _effect.emit(e) }

    /**
     * [bindError]
     * - Subscribes to the error flow and executes the provided action whenever an error is emitted.
     * - Typically called within the ViewModel's `init` block.
     */
    protected fun bindError(
        scope: CoroutineScope = viewModelScope,
        action: suspend (ERROR) -> Unit,
    ) = error.onEach { action(it) }.launchIn(scope)

    /**
     * [bindEvent]
     * - Subscribes to the event channel and executes the provided action whenever an event is received.
     * - Typically called within the ViewModel's `init` block to set up the event loop (MVI intent handler).
     */
    protected fun bindEvent(
        scope: CoroutineScope = viewModelScope,
        action: suspend (EVENT) -> Unit,
    ) = event.onEach { action(it) }.launchIn(scope)

    /**
     * [bindEffect]
     * - Subscribes to the effect flow from the UI layer.
     * - Must be called from the View (e.g., inside a `LaunchedEffect` in Compose) passing its own lifecycle-aware scope.
     */
    fun bindEffect(
        scope: CoroutineScope,
        action: suspend (EFFECT) -> Unit
    ) = effect.onEach { action(it) }.launchIn(scope)
}