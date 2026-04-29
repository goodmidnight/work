package io.goodmidnight.transfer.core.connection.connectivity

data class ConnectivityState(
    /**
     * True if a target P2P network (Wi-Fi/Hotspot) is detected and available.
     */
    val isWifiAvailable: Boolean = false,

    /**
     * True if the application process is successfully bound to the P2P network,
     * ensuring traffic is not routed to cellular data.
     */
    val isBound: Boolean = false,

    /**
     * The unique identifier of the currently bound network.
     */
    val boundNetworkId: String? = null,

    /**
     * The assigned IPv4 address of the device on the bound network.
     */
    val localIp: String? = null
)