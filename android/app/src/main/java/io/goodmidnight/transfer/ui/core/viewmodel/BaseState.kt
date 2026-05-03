package io.goodmidnight.transfer.ui.core.viewmodel

/**
 * [BaseState]
 * - Base interface for defining the immutable state of a screen.
 * - Enforces the implementation of common UI and Screen state descriptors.
 */
interface BaseState {
    val uiState: UiState
    val screenState: ScreenState
}