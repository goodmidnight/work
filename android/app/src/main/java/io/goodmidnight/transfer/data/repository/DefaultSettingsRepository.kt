package io.goodmidnight.transfer.data.repository

import io.goodmidnight.transfer.data.datastore.SettingsDataSource
import io.goodmidnight.transfer.domain.model.SettingsData
import io.goodmidnight.transfer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SettingsRepository] that acts as an abstraction layer over the raw data source.
 */
@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val settingsDataSource: SettingsDataSource,
) : SettingsRepository {

    // Exposes a continuous stream of the latest settings.
    override val settingsFlow: Flow<SettingsData> = settingsDataSource.settingsFlow

    // Fetches the current settings data exactly once.
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