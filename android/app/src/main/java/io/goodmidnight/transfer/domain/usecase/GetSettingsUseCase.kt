package io.goodmidnight.transfer.domain.usecase

import io.goodmidnight.transfer.domain.model.SettingsData
import io.goodmidnight.transfer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for observing the application settings.
 *
 * @return A [Flow] that continuously emits the latest [SettingsData] whenever a preference changes.
 */
class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<SettingsData> = repository.settingsFlow
}