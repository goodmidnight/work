package io.goodmidnight.transfer.ui.core.viewmodel

/**
 * [UiState]
 * - Represents the micro-states of UI components on a screen.
 * - Useful for granular UI updates (e.g., disabling a button while saving, showing a small spinner while paginating).
 */
data class UiState(
    val isFailure: Boolean = false,
    val isLoading: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaginating: Boolean = false,
    val isUpdating: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSearching: Boolean = false,
    val isOffline: Boolean = false
)