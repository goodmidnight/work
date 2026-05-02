package io.goodmidnight.transfer.domain.model

/**
 * Represents the user's configurable settings for the application.
 *
 * @property deviceName The display name of this device broadcasted or shown to peers.
 * @property saveLocation The absolute directory path where received files will be saved.
 */
data class SettingsData(
    val deviceName: String,
    val saveLocation: String
)