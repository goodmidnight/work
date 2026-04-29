package io.goodmidnight.transfer.core.connection.connectivity

sealed interface ConnectivityEffect {

    /**
     * Emitted when the process is successfully bound to the requested network.
     */
    data class BindingEstablished(val networkId: String) : ConnectivityEffect

    /**
     * Emitted when the auto-connection to the hotspot and the process binding are both complete,
     * indicating that data transfer can immediately begin.
     */
    data object HotspotConnectedAndBound : ConnectivityEffect
}