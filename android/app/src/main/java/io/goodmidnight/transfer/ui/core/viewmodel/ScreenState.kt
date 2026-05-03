package io.goodmidnight.transfer.ui.core.viewmodel

/**
 * [ScreenState]
 * - Represents the overarching macro-state of a screen.
 * - Useful for displaying full-screen overlays (e.g., a full-screen loading spinner, an empty view, or a network error screen).
 */
enum class ScreenState {
    INITIAL,
    LOADING,
    SUCCESS,
    FAILURE,
    EMPTY,
    OFFLINE,
    TIMEOUT,
}