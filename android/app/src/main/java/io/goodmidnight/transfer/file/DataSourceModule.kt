package io.goodmidnight.transfer.file

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.goodmidnight.transfer.data.datasource.AndroidFileDataSource
import io.goodmidnight.transfer.data.datasource.SettingsDataSource
import io.goodmidnight.transfer.datastore.DefaultSettingsDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule {

    /**
     * Binds the DefaultSettingsDataSource to the SettingsDataSource interface.
     */
    @Binds
    @Singleton
    fun bindAndroidFileDataSource(
        fileDataSource: DefaultAndroidFileDataSource,
    ): AndroidFileDataSource
}