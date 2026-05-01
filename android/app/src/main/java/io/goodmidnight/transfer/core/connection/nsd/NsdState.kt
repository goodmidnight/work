package io.goodmidnight.transfer.core.connection.nsd

import io.goodmidnight.transfer.core.connection.model.Peer

data class NsdState(
    /**
     * True if the device is currently searching the local network for other peers.
     */
    val isDiscovering: Boolean = false,

    /**
     * True if the device is currently advertising its own service.
     */
    val isBroadcasting: Boolean = false,

    /**
     * A list of unique peers found on the local network via NSD.
     */
    val discoveredPeers: List<Peer> = emptyList(),

    /**
     * The final service name assigned to this device by the OS.
     * Used to filter out our own broadcast from the discovery results.
     */
    val myDeviceName: String = "",
)