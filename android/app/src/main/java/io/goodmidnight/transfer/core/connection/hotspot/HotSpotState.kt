package io.goodmidnight.transfer.core.connection.hotspot

data class HotSpotState(
    /**
     * True if the temporary Wi-Fi Access Point (AP) is successfully created,
     * currently active, and ready to accept incoming peer connections.
     */
    val isActive: Boolean = false,

    /**
     *  The dynamically generated Service Set Identifier (Network Name) of the hotspot.
     * Other devices will scan for this specific SSID to join the P2P network.
     */
    val ssid: String = "",

    /**
     * The auto-generated WPA2 Pre-Shared Key (Password) for the hotspot.
     * The client device must use this password to authenticate and connect to the AP securely.
     */
    val password: String = ""
)