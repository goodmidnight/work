package io.goodmidnight.transfer.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.theme.Theme

@Composable
fun Snackbar(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
    ) { snackbarData ->
        SnackbarComponent(snackbarData.visuals.message)
    }
}

@Composable
private fun SnackbarComponent(message: String) {
    Box(
        modifier = Modifier
            .systemBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .background(
                color = Theme.colorScheme.accent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        BodyLargeText(
            text = message,
            color = Theme.colorScheme.onAccent
        )
    }
}