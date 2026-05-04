package io.goodmidnight.transfer.ui.feature.transfer.home.data

import androidx.compose.runtime.Immutable
import io.goodmidnight.transfer.ui.core.viewmodel.BaseState
import io.goodmidnight.transfer.ui.core.viewmodel.ScreenState
import io.goodmidnight.transfer.ui.core.viewmodel.UiState

@Immutable
data class HomeState(
    override val uiState: UiState = UiState(),
    override val screenState: ScreenState = ScreenState.INITIAL,
    val deviceName: String = ""
) : BaseState {


}
