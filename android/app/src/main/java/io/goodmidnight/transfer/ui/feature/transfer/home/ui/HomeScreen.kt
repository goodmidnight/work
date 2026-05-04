package io.goodmidnight.transfer.ui.feature.transfer.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.designsystem.component.BodyLargeText
import io.goodmidnight.transfer.designsystem.component.BodyMediumText
import io.goodmidnight.transfer.designsystem.component.Card
import io.goodmidnight.transfer.designsystem.component.CardStyle
import io.goodmidnight.transfer.designsystem.component.HeadlineLargeText
import io.goodmidnight.transfer.designsystem.component.LabelMediumText
import io.goodmidnight.transfer.designsystem.component.MainTopBar
import io.goodmidnight.transfer.designsystem.component.PrimaryButton
import io.goodmidnight.transfer.designsystem.component.SecondaryButton
import io.goodmidnight.transfer.designsystem.component.TitleMediumText
import io.goodmidnight.transfer.designsystem.preview.ComponentPreview
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.feature.transfer.home.data.HomeState
import io.goodmidnight.transfer.ui.feature.transfer.shared.SharedState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeState,
    sharedState: SharedState,
    onSettingsClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onSendPhotosClick: () -> Unit,
    onSendDocumentsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onPeerClick: (Peer) -> Unit,
    onCancelHosting: () -> Unit,
    onCancelDiscovery: () -> Unit,
) {
    Scaffold(
        topBar = {
            MainTopBar(
                title = "Transfer",
                actions = {
                    if (!sharedState.isHosting && !sharedState.isDiscovering) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = Theme.colorScheme.icon
                            )
                        }
                    }
                }
            )
        },
        containerColor = Theme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                sharedState.isHosting -> {
                    HostingView(
                        sharedState = sharedState,
                        onCancelClick = onCancelHosting
                    )
                }

                sharedState.isDiscovering -> {
                    SendDiscoveryView(
                        peers = sharedState.discoveredPeers,
                        onPeerClick = onPeerClick,
                        onCancelClick = onCancelDiscovery
                    )
                }

                else -> {
                    DefaultHomeView(
                        deviceName = state.deviceName,
                        onQrScanClick = onQrScanClick,
                        onSendPhotosClick = onSendPhotosClick,
                        onSendDocumentsClick = onSendDocumentsClick,
                        onReceiveClick = onReceiveClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultHomeView(
    deviceName: String,
    onQrScanClick: () -> Unit,
    onSendPhotosClick: () -> Unit,
    onSendDocumentsClick: () -> Unit,
    onReceiveClick: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        BodyMediumText(
            text = "My Device: $deviceName",
            color = Theme.colorScheme.secondaryText,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(style = CardStyle.OUTLINED) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TitleMediumText(text = "Send")
                PrimaryButton(
                    text = "Photos / Videos",
                    onClick = onSendPhotosClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
                PrimaryButton(
                    text = "Files / Docs",
                    onClick = onSendDocumentsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
                SecondaryButton(
                    text = "Scan QR Code (Manual)",
                    onClick = onQrScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Receive Section
        Card(style = CardStyle.DEFAULT) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TitleMediumText(text = "Receive")
                PrimaryButton(
                    text = "Receive Files (Radar & QR)",
                    onClick = onReceiveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SendDiscoveryView(
    peers: List<Peer>,
    onPeerClick: (Peer) -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Theme.colorScheme.accent,
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))
        HeadlineLargeText(text = "Searching...")
        Spacer(modifier = Modifier.height(8.dp))
        BodyMediumText(
            text = "Select a nearby device to send your files.",
            color = Theme.colorScheme.secondaryText
        )

        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(peers) { peer ->
                Card(
                    style = CardStyle.VARIANT,
                    modifier = Modifier.clickable { onPeerClick(peer) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyLargeText(text = peer.deviceName)
                        LabelMediumText(
                            text = peer.type.name,
                            color = Theme.colorScheme.secondaryText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SecondaryButton(
            text = "Cancel",
            onClick = onCancelClick,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp) // Height is applied securely
        )
        Spacer(modifier = Modifier.height(32.dp)) // Safe area margin extracted
    }
}

@Composable
private fun HostingView(sharedState: SharedState, onCancelClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeadlineLargeText(text = "Ready to Receive")
        Spacer(modifier = Modifier.height(16.dp))

        BodyMediumText(
            text = "Your device is visible to nearby senders.\nSelect your device from their radar, or scan the QR code below.",
            color = Theme.colorScheme.secondaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(style = CardStyle.OUTLINED) {
            Box(
                modifier = Modifier.padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                sharedState.qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Connection QR Code",
                        modifier = Modifier.size(240.dp) // Ensures the QR code never stretches unpredictably
                    )
                } ?: CircularProgressIndicator(color = Theme.colorScheme.accent)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        SecondaryButton(
            text = "Cancel",
            onClick = onCancelClick,
            modifier = Modifier
                .width(200.dp)
                .height(56.dp)
        )
    }
}

@Composable
@ComponentPreview
private fun HomeScreenDiscoveringPreview() {
    val dummyPeers = List(15) { index ->
        Peer(address = "$index", deviceName = "Device $index", type = Peer.Type.WIFI_DIRECT)
    }

    Theme {
        HomeScreen(
            state = HomeState(
                deviceName = "Pixel 8 Pro",
            ),
            sharedState = SharedState(
                isHosting = false,
                isDiscovering = false,
                discoveredPeers = dummyPeers
            ),
            onSettingsClick = {},
            onQrScanClick = {},
            onSendPhotosClick = {},
            onSendDocumentsClick = {},
            onReceiveClick = {},
            onPeerClick = {},
            onCancelHosting = {},
            onCancelDiscovery = {}
        )
    }
}

@Composable
@ComponentPreview
private fun HomeScreenHostingPreview() {
    Theme {
        HomeScreen(
            state = HomeState(
                deviceName = "Pixel 8 Pro",
            ),
            sharedState = SharedState(
                isHosting = true,
                isDiscovering = false,
                qrBitmap = null
            ),
            onSettingsClick = {},
            onQrScanClick = {},
            onSendPhotosClick = {},
            onSendDocumentsClick = {},
            onReceiveClick = {},
            onPeerClick = {},
            onCancelHosting = {},
            onCancelDiscovery = {}
        )
    }
}