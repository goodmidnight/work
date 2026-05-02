package io.goodmidnight.transfer.domain.usecase

import io.goodmidnight.transfer.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * UseCase for updating the default directory where received files are saved.
 */
class UpdateSaveLocationUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(location: String) {
        val validLocation = location.trim()
        if (validLocation.isNotBlank()) {
            repository.updateSaveLocation(validLocation)
        }
    }
}