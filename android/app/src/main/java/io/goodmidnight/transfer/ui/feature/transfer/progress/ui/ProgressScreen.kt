package io.goodmidnight.transfer.ui.feature.transfer.progress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.component.BodyLargeText
import io.goodmidnight.transfer.designsystem.component.BodyMediumText
import io.goodmidnight.transfer.designsystem.component.CardStyle
import io.goodmidnight.transfer.designsystem.component.HeadlineLargeText
import io.goodmidnight.transfer.designsystem.component.PrimaryButton
import io.goodmidnight.transfer.designsystem.component.SecondaryButton
import io.goodmidnight.transfer.designsystem.component.TitleLargeText
import io.goodmidnight.transfer.designsystem.component.TitleMediumText
import io.goodmidnight.transfer.designsystem.component.Card
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.feature.transfer.progress.data.ProgressState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState

/**
 * Stateless UI component displaying the current status of an ongoing P2P file transfer.
 * Adapts its layout based on the global TransferStatus enum.
 */
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    state: ProgressState,
    sharedState: SharedState,
    onCancelClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (sharedState.transferStatus) {
            SharedState.TransferStatus.CONNECTING -> {
                ConnectingView(onCancelClick = onCancelClick)
            }
            SharedState.TransferStatus.TRANSFERRING -> {
                TransferringView(
                    fileName = sharedState.currentFileName,
                    progress = sharedState.currentProgress,
                    onCancelClick = onCancelClick
                )
            }
            SharedState.TransferStatus.COMPLETED -> {
                CompletedView(onDoneClick = onDoneClick)
            }
            SharedState.TransferStatus.ERROR -> {
                ErrorView(onDoneClick = onDoneClick)
            }
            SharedState.TransferStatus.IDLE -> {
                // Maintained as empty; Route will handle popping the back stack.
            }
        }
    }
}

@Composable
private fun ConnectingView(onCancelClick: () -> Unit) {
    CircularProgressIndicator(
        modifier = Modifier.size(64.dp),
        color = Theme.colorScheme.accent,
        strokeWidth = 6.dp,
        strokeCap = StrokeCap.Round
    )
    Spacer(modifier = Modifier.height(32.dp))
    HeadlineLargeText(text = "Connecting...")
    Spacer(modifier = Modifier.height(8.dp))
    BodyMediumText(
        text = "Establishing a secure connection with the peer.",
        color = Theme.colorScheme.secondaryText
    )
    Spacer(modifier = Modifier.height(48.dp))
    SecondaryButton(
        text = "Cancel",
        onClick = onCancelClick,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
}

@Composable
private fun TransferringView(
    fileName: String,
    progress: Int,
    onCancelClick: () -> Unit
) {
    HeadlineLargeText(text = "Transferring")
    Spacer(modifier = Modifier.height(40.dp))

    Card(style = CardStyle.OUTLINED) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TitleMediumText(
                text = fileName.ifEmpty { "Preparing file..." },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                // Coerced to float to ensure accurate rendering of the progress bar
                progress = { progress.toFloat() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Theme.colorScheme.accent,
                trackColor = Theme.colorScheme.outline,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyLargeText(
                    text = "$progress%",
                    color = Theme.colorScheme.accent
                )
                BodyMediumText(
                    text = if (progress == 100) "Finalizing..." else "Sending",
                    color = Theme.colorScheme.secondaryText
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(48.dp))
    SecondaryButton(
        text = "Cancel",
        onClick = onCancelClick,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
}

@Composable
private fun CompletedView(onDoneClick: () -> Unit) {
    TitleLargeText(
        text = "Transfer Complete!",
        color = Theme.colorScheme.success // Semantic success indicator
    )
    Spacer(modifier = Modifier.height(16.dp))
    BodyMediumText(
        text = "All files have been successfully transferred.",
        color = Theme.colorScheme.secondaryText
    )

    Spacer(modifier = Modifier.height(48.dp))
    PrimaryButton(
        text = "Done",
        onClick = onDoneClick,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
}

@Composable
private fun ErrorView(onDoneClick: () -> Unit) {
    TitleLargeText(
        text = "Transfer Failed",
        color = Theme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(16.dp))
    BodyMediumText(
        text = "Connection lost or an error occurred during transfer.",
        color = Theme.colorScheme.secondaryText
    )

    Spacer(modifier = Modifier.height(48.dp))
    PrimaryButton(
        text = "Go Back",
        onClick = onDoneClick,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
}

// ============================================================================
// Previews
// ============================================================================

@Composable
@ComponentPreview
private fun ProgressScreenConnectingPreview() {
    Theme {
        ProgressScreen(
            state = ProgressState(),
            sharedState = SharedState(transferStatus = SharedState.TransferStatus.CONNECTING),
            onCancelClick = {},
            onDoneClick = {}
        )
    }
}

@Composable
@ComponentPreview
private fun ProgressScreenTransferringPreview() {
    Theme {
        ProgressScreen(
            state = ProgressState(),
            sharedState = SharedState(
                transferStatus = SharedState.TransferStatus.TRANSFERRING,
                currentFileName = "Vacation_Video_2026_Full.mp4",
                currentProgress = 67
            ),
            onCancelClick = {},
            onDoneClick = {}
        )
    }
}

@Composable
@ComponentPreview
private fun ProgressScreenCompletedPreview() {
    Theme {
        ProgressScreen(
            state = ProgressState(),
            sharedState = SharedState(transferStatus = SharedState.TransferStatus.COMPLETED),
            onCancelClick = {},
            onDoneClick = {}
        )
    }
}

@Composable
@ComponentPreview
private fun ProgressScreenErrorPreview() {
    Theme {
        ProgressScreen(
            state = ProgressState(),
            sharedState = SharedState(transferStatus = SharedState.TransferStatus.ERROR),
            onCancelClick = {},
            onDoneClick = {}
        )
    }
}