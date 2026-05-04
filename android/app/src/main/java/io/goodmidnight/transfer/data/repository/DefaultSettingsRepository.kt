package io.goodmidnight.transfer.data.repository

import io.goodmidnight.transfer.data.datasource.SettingsDataSource
import io.goodmidnight.transfer.domain.model.SettingsData
import io.goodmidnight.transfer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DefaultSettingsRepository]
 * - Concrete implementation of SettingsRepository following Clean Architecture.
 * - It abstracts the underlying data source (e.g., DataStore, SharedPreferences), ensuring the Domain layer remains decoupled from Android-specific storage frameworks.
 */
@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val settingsDataSource: SettingsDataSource,
) : SettingsRepository {

    // Exposes a continuous, reactive stream of settings.
    // Any changes to the underlying storage will automatically emit a new SettingsData object.
    override val settingsFlow: Flow<SettingsData> = settingsDataSource.settingsFlow

    // Fetches the current settings data exactly once (one-shot read).
    // Useful for initializations where continuous observation is unnecessary.
    override suspend fun fetchCurrentSettings(): SettingsData? {
        return settingsDataSource.fetchCurrentSettings()
    }

    override suspend fun updateDeviceName(name: String) {
        settingsDataSource.updateDeviceName(name)
    }

    override suspend fun updateSaveLocation(location: String) {
        settingsDataSource.updateSaveLocation(location)
    }
}