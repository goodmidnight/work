package io.goodmidnight.transfer.ui.core.utils

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf


/**
 * A [staticCompositionLocalOf] that provides the [SnackbarHostState] across the Compose tree.
 * Using 'staticCompositionLocalOf' improves performance since the host state instance rarely changes.
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    noLocalProvidedFor("LocalSnackbarHostState")
}
private fun noLocalProvidedFor(name: String): Nothing =
    error("CompositionLocal $name not present")