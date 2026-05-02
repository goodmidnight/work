package io.goodmidnight.transfer.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transfer_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Provides a singleton instance of DataStore<Preferences>.
     * This decouples the DataStore creation logic from the Repository.
     * 
     * @param context The application context provided by Hilt.
     * @return A DataStore instance configured for settings.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }
}