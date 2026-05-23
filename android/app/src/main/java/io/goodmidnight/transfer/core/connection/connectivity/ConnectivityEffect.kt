package io.goodmidnight.transfer.core.connection.connectivity

sealed interface ConnectivityEffect {

    /**
     * Emitted when the process is successfully bound to the requested network.
     * @param networkId The unique identifier of the bound network.
     * @param localIp The IP address assigned to this device on the network.
     */
    data class BindingEstablished(val networkId: String, val localIp: String?) : ConnectivityEffect

    /**
     * Emitted when the auto-connection to the hotspot and the process binding are both complete.
     * @param networkId The unique identifier of the bound network.
     * @param localIp The IP address assigned to this device on the network.
     */
    data class HotspotConnectedAndBound(val networkId: String, val localIp: String?) : ConnectivityEffect

    /**
     * Emitted when the active network connection is lost.
     */
    data class NetworkLost(val networkId: String?) : ConnectivityEffect
}
