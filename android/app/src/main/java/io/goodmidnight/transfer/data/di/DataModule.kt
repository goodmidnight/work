package io.goodmidnight.transfer.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.goodmidnight.transfer.data.repository.DefaultSettingsRepository
import io.goodmidnight.transfer.data.repository.DefaultTransferRepository
import io.goodmidnight.transfer.domain.repository.SettingsRepository
import io.goodmidnight.transfer.domain.repository.TransferRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Singleton
    @Binds
    fun bindSettingsRepository(
        repository: DefaultSettingsRepository,
    ): SettingsRepository

    @Singleton
    @Binds
    fun bindTransferRepository(
        repository: DefaultTransferRepository,
    ): TransferRepository
}