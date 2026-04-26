package io.goodmidnight.transfer.core.controller.base

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A generic base class for all connection controllers.
 */
abstract class BaseController<STATE, EVENT, EFFECT, ERROR>(
    controllerName: String,
    initialState: STATE
) {
    protected val controllerScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName(controllerName))

    private val _state by lazy { MutableStateFlow(initialState) }
    val state: StateFlow<STATE> by lazy { _state.asStateFlow() }

    /** Helper method to atomically update the state within the controller. */
    protected fun updateState(action: STATE.() -> STATE) {
        _state.update(action)
    }

    private val _event = Channel<EVENT>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    init {
        observeEventsInternal()
    }

    /** Exposes a thread-safe method for external components to send events. */
    suspend fun processEvent(event: EVENT) {
        _event.send(event)
    }

    /** Internal event loop. Subclasses MUST implement handleEvent to process incoming events. */
    private fun observeEventsInternal() {
        controllerScope.launch {
            _event.receiveAsFlow().collect { event ->
                handleEvent(event)
            }
        }
    }

    /** Abstract method where subclasses define how to react to specific events. */
    protected abstract suspend fun handleEvent(event: EVENT)

    private val _effect = MutableSharedFlow<EFFECT>()
    val effect: SharedFlow<EFFECT> = _effect.asSharedFlow()

    /** Helper method to emit effects securely to external subscribers. */
    protected fun emitEffect(effect: EFFECT) {
        controllerScope.launch {
            _effect.emit(effect)
        }
    }

    private val _error = MutableSharedFlow<ERROR>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val error: SharedFlow<ERROR> = _error.asSharedFlow()

    /** Helper method to emit errors securely to external subscribers. */
    protected fun emitError(error: ERROR) {
        controllerScope.launch {
            _error.emit(error)
        }
    }

    /**
     * Cleans up the controller's coroutine scope.
     * Should be called when the controller is no longer needed to prevent memory leaks.
     */
    open fun clear() {
        controllerScope.cancel()
    }
}