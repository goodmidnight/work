package io.goodmidnight.transfer.core.connection.nsd

import io.goodmidnight.transfer.core.connection.model.Peer

/**
 * Represents the current UI/Data state of the Network Service Discovery (NSD) operations.
 * This state is continuously observed by the ConnectionManager to update the UI.
 */
data class NsdState(

    /**
     * True if the device is currently searching the local network for other peers.
     * Use this to toggle radar animations or loading indicators in the UI.
     */
    val isDiscovering: Boolean = false,

    /**
     * True if the device is currently advertising its own service
     * so that other peers on the same Wi-Fi can discover it.
     */
    val isBroadcasting: Boolean = false,

    /**
     * A list of unique peers found on the local network via NSD.
     * This list should be directly bound to the UI's RecyclerView or LazyColumn.
     */
    val discoveredPeers: List<Peer> = emptyList(),

    /**
     * The final service name assigned to this device by the OS (e.g., "Galaxy S24 (1)").
     * This is used internally to filter out our own broadcast from the discovery results.
     */
    val myDeviceName: String = ""
)