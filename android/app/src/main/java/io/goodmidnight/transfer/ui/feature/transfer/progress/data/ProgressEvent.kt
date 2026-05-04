package io.goodmidnight.transfer.ui.feature.transfer.progress.data

import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface ProgressEvent : BaseEvent {
    data object OnBack : ProgressEvent
}
