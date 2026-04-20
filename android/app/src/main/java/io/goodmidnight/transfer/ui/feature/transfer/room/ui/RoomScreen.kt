package io.goodmidnight.transfer.ui.feature.transfer.room.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.feature.transfer.room.data.RoomState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    modifier: Modifier = Modifier,
    state: RoomState,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {

    }
}

@Composable
@ComponentPreview
fun RoomScreenPreview(
    modifier: Modifier = Modifier,
) {
    Theme {}
}