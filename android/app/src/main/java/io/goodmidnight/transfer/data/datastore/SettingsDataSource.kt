package io.goodmidnight.transfer.data.datastore

import io.goodmidnight.transfer.domain.model.SettingsData
import kotlinx.coroutines.flow.Flow

interface SettingsDataSource {
    val settingsFlow: Flow<SettingsData>
    suspend fun fetchCurrentSettings(): SettingsData?
    suspend fun updateDeviceName(name: String)
    suspend fun updateSaveLocation(location: String)
}