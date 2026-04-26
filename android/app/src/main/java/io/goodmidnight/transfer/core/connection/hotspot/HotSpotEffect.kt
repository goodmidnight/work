package io.goodmidnight.transfer.core.connection.hotspot

import io.goodmidnight.transfer.core.exception.ApplicationException

sealed interface HotSpotEffect {
    data class SendError(val exception: ApplicationException) : HotSpotEffect
}