package io.goodmidnight.transfer.datastore

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.goodmidnight.transfer.data.datastore.SettingsDataSource
import io.goodmidnight.transfer.data.exception.DataException
import io.goodmidnight.transfer.domain.model.SettingsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DefaultSettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsDataSource {

    // Define keys for the DataStore preferences
    private object PreferencesKeys {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val SAVE_LOCATION = stringPreferencesKey("save_location")
    }

    override val settingsFlow: Flow<SettingsData> = dataStore.data
        .catch { exception ->
            // If an error occurs reading from DataStore, emit empty preferences instead of crashing
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw DataException.DataStoreReadException(cause = exception)
            }
        }
        .map { preferences ->
            // Provide default values if the keys do not exist in the DataStore yet
            val defaultDeviceName = Build.MODEL ?: "Transfer_Device"
            val deviceName = preferences[PreferencesKeys.DEVICE_NAME] ?: defaultDeviceName
            val saveLocation = preferences[PreferencesKeys.SAVE_LOCATION] ?: "Downloads/Transfer"

            SettingsData(deviceName, saveLocation)
        }

    override suspend fun fetchCurrentSettings(): SettingsData {
        // Collect the first item emitted by the flow and immediately cancel collection
        return settingsFlow.first()
    }

    override suspend fun updateDeviceName(name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_NAME] = name
        }
    }

    override suspend fun updateSaveLocation(location: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVE_LOCATION] = location
        }
    }
}