package io.goodmidnight.transfer.core.connection.bluetooth

/**
 * Represents one-time events (side-effects) emitted by the BluetoothController.
 */
sealed interface BluetoothEffect {
    /**
     * GATT 통신을 통해 상대방의 전체 핫스팟/소켓 정보를 성공적으로 읽어왔을 때 발생합니다.
     */
    data class HandshakeCompleted(
        val ip: String,
        val port: Int,
        val ssid: String,
        val pw: String
    ) : BluetoothEffect
}