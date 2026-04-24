package io.goodmidnight.transfer.core.connection.bluetooth

/**
 * A utility object to serialize (compress) and deserialize an IP address string
 * and port number into a ByteArray to fit within a BLE advertisement packet.
 */
object BleDataSerializer {

    /**
     * Compresses an IPv4 address string (e.g., "192.168.43.1") and a port number
     * (e.g., 8080) into a 6-byte ByteArray.
     * * @return A 6-byte array formatted as [IP1, IP2, IP3, IP4, PortHigh, PortLow].
     * @throws IllegalArgumentException if the IP address format is invalid.
     */
    fun serialize(ip: String, port: Int): ByteArray {
        val parts = ip.split(".")
        require(parts.size == 4) { "Invalid IPv4 format: $ip" }

        val byteArray = ByteArray(6)

        // 1. Compress IP address (4 bytes)
        for (i in 0..3) {
            val octet = parts[i].toIntOrNull()
            require(octet != null && octet in 0..255) { "Invalid IP octet: ${parts[i]}" }

            // Note: Kotlin's toByte() casts values to a signed range (-128 to 127).
            // Values >= 128 will overflow into negative numbers, but they will be
            // safely restored during deserialization using a bitwise AND operation.
            byteArray[i] = octet.toByte()
        }

        // 2. Compress port number (2 bytes, Big Endian)
        byteArray[4] = (port shr 8).toByte() // Upper 8 bits
        byteArray[5] = port.toByte()         // Lower 8 bits

        return byteArray
    }

    /**
     * Deserializes a 6-byte ByteArray back into an IP address string and a port number.
     * * @return A Pair containing the decoded IP address string and the port number.
     * @throws IllegalArgumentException if the data array length is not exactly 6.
     */
    fun deserialize(data: ByteArray): Pair<String, Int> {
        require(data.size == 6) { "Invalid data length for deserialization: ${data.size}" }

        // 1. Deserialize IP address
        // Use `toInt() and 0xFF` to convert the signed byte back to an unsigned integer (0~255).
        val ip = "${data[0].toInt() and 0xFF}." +
                "${data[1].toInt() and 0xFF}." +
                "${data[2].toInt() and 0xFF}." +
                "${data[3].toInt() and 0xFF}"

        // 2. Deserialize port number (Big Endian)
        val portHigh = data[4].toInt() and 0xFF
        val portLow = data[5].toInt() and 0xFF
        val port = (portHigh shl 8) or portLow

        return Pair(ip, port)
    }
}