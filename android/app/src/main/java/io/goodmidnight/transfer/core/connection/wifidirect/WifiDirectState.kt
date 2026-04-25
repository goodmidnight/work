package io.goodmidnight.transfer.core.connection.wifidirect

import io.goodmidnight.transfer.core.connection.model.Peer

/**
 * Represents the current UI and operational state of the Wi-Fi Direct connection process.
 * This state is continuously observed by the ConnectionManager to update the UI.
 */
data class WifiDirectState(
    /** * True if the device is currently scanning for nearby Wi-Fi Direct peers.
     */
    val isDiscovering: Boolean = false,

    /** * True if this device is the Group Owner (Hub/Server) of the established P2P network.
     * False if it is a Client, or if no connection is established.
     */
    val isGroupOwner: Boolean = false,

    /** * True if a Wi-Fi Direct connection has been successfully established with a peer.
     */
    val isConnected: Boolean = false,

    /** * A list of nearby peers discovered via Wi-Fi Direct scanning.
     */
    val discoveredPeers: List<Peer> = emptyList()
)