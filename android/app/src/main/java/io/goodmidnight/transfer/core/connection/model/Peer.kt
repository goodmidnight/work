package io.goodmidnight.transfer.core.connection.model

/**
 * A unified domain model representing a nearby device discovered
 * through various network protocols (NSD, Wi-Fi Direct, BLE, etc.).
 * This isolates the UI and Domain layers from the underlying network implementation details.
 *
 * @property deviceName The human-readable name of the device (e.g., "Galaxy S24").
 * @property address The network identifier. Can be a resolved IP address (NSD) or a MAC address (Wi-Fi Direct).
 * @property port The socket port number designated for the actual data transfer phase.
 * @property type The specific protocol used to discover this device.
 */
data class Peer(
    val deviceName: String,
    val address: String,
    val port: Int = 8080,
    val type: Type,
) {
    /**
     * Defines the underlying protocol used for peer discovery.
     */
    enum class Type {
        /**
         * Discovered on the exact same Wi-Fi network. Address is a resolved IP.
         */
        NSD,

        /**
         * Discovered via Wi-Fi Direct. Address is typically a MAC address prior to group formation.
         */
        WIFI_DIRECT,

        /**
         *  Discovered via Bluetooth Low Energy (BLE) scanning.
         */
        BLUETOOTH,

        /** Represents a Local Only Hotspot Access Point. */
        HOTSPOT,

        /**
         * Fallback for an undefined or unhandled discovery method.
         */
        UNKNOWN
    }

    /**
     * A composite identifier used to uniquely distinguish peers in UI components
     * (e.g., LazyColumn/RecyclerView) and to filter out duplicates reliably.
     */
    val uniqueId: String
        get() = "${type.name}_$address"
}