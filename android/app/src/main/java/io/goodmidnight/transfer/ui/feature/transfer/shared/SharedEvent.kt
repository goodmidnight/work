package io.goodmidnight.transfer.ui.feature.transfer.shared

import android.net.Uri
import io.goodmidnight.transfer.core.connection.ConnectionState
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.ui.core.viewmodel.BaseEvent

sealed interface SharedEvent : BaseEvent {
    // Queues files for the upcoming transfer session.
    data class OnSelectFiles(val uris: List<Uri>) : SharedEvent

    // Initiates a scan for nearby devices acting as hosts.
    data class OnStartDiscovery(
        val mode: ConnectionState.ConnectionMode = ConnectionState.ConnectionMode.AUTO
    ) : SharedEvent

    // Halts the active discovery process.
    data object OnStopDiscovery : SharedEvent

    // Requests an active socket connection to a specific discovered peer.
    data class OnConnectToPeer(val peer: Peer) : SharedEvent

    // Sets up this device as a host (Access Point / BLE Broadcaster) to receive files.
    data class OnStartHosting(
        val deviceName: String,
        val mode: ConnectionState.ConnectionMode = ConnectionState.ConnectionMode.AUTO,
    ) : SharedEvent

    // Tears down the active hosting session.
    data object OnStopHosting : SharedEvent

    // Aborts the current file transfer and disconnects the socket.
    data object OnCancelTransfer : SharedEvent

    // Uses scanned QR credentials to connect directly to the sender's hotspot.
    data class OnConnectQr(val ip: String, val ssid: String, val pw: String) : SharedEvent

    // Checks for external files shared via OS-level "Share Sheet" or Deep Links.
    data object OnCheckPendingFiles : SharedEvent
}