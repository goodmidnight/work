package io.goodmidnight.transfer.domain.usecase

import io.goodmidnight.transfer.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * UseCase for updating the device's display name used during peer discovery.
 */
class UpdateDeviceNameUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(name: String) {
        val validName = name.trim()
        if (validName.isNotBlank()) {
            repository.updateDeviceName(validName)
        }
    }
}