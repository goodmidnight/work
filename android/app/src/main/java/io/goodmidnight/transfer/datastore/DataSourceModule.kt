package io.goodmidnight.transfer.datastore

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.goodmidnight.transfer.data.datastore.SettingsDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule {

    /**
     * Binds the DefaultSettingsDataSource to the SettingsDataSource interface.
     */
    @Binds
    @Singleton
    fun bindSettingsDataSource(
        defaultSettingsDataSource: DefaultSettingsDataSource,
    ): SettingsDataSource
}