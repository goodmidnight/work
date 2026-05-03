package io.goodmidnight.transfer.ui.core.utils

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Immediately dismisses any currently active Snackbar and displays a new one.
 * Allows for a custom display duration override.
 *
 * @param coroutineScope The [CoroutineScope] used to launch the snackbar.
 * @param message The text message to display.
 * @param duration The custom display time in milliseconds before the snackbar is forcibly dismissed (default is 2000ms).
 * @param actionLabel The optional action label to display on the snackbar.
 * @param withDismissAction Whether to include a close icon (dismiss action).
 */
fun SnackbarHostState.showSnackbarImmediately(
    coroutineScope: CoroutineScope,
    message: String,
    duration: Long = 2000L,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
) {
    coroutineScope.launch {
        // Instantly dismiss the existing snackbar to prevent stacking delays
        currentSnackbarData?.dismiss()

        // Launch the new snackbar in a separate job so we can control its exact lifecycle
        val snackbarJob = launch {
            showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
            )
        }

        // Wait for the specified duration, then forcibly dismiss it
        delay(duration)
        snackbarJob.cancelAndJoin()
    }
}