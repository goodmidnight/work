package io.goodmidnight.transfer.ui.core.viewmodel

import io.goodmidnight.transfer.core.exception.ApplicationException

/**
 * [BaseError]
 * - Marker interface for defining domain or application-level errors
 *   that are passed from the ViewModel to the UI.
 */
interface BaseError {
    val cause: ApplicationException
}
