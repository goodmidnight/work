package io.goodmidnight.transfer.ui.feature.permission.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.designsystem.component.BodyLargeText
import io.goodmidnight.transfer.designsystem.component.HeadlineLargeText
import io.goodmidnight.transfer.designsystem.component.PrimaryButton
import io.goodmidnight.transfer.designsystem.component.SecondaryButton
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.feature.permission.data.PermissionState

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    state: PermissionState,
    onRequestClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeadlineLargeText(
            text = "Permissions for\nSeamless Transfers",
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        BodyLargeText(
            text = "To enable secure, serverless P2P file sharing, we need access to nearby devices (Wi-Fi, Bluetooth) and your camera (for QR scanning).",
            textAlign = TextAlign.Center,
            color = Theme.colorScheme.secondaryText
        )

        Spacer(modifier = Modifier.height(48.dp))

        PrimaryButton(
            text = "Grant Permissions",
            onClick = onRequestClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        SecondaryButton(
            text = "Open System Settings",
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
@ComponentPreview
private fun PermissionScreenPreview() {
    Theme {
        PermissionScreen(
            state = PermissionState(),
            onRequestClick = {},
            onSettingsClick = {}
        )
    }
}