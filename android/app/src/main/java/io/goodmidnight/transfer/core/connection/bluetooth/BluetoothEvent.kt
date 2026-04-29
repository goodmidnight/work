package io.goodmidnight.transfer.core.connection.bluetooth

/**
 * Defines all user intents or commands that can be sent to the BluetoothController.
 */
sealed interface BluetoothEvent {
    /** Starts scanning for nearby BLE devices broadcasting the specific Service UUID. */
    data object StartScan : BluetoothEvent

    /** Stops the ongoing BLE scan. */
    data object StopScan : BluetoothEvent

    /**
     * Starts broadcasting via BLE.
     * UUID만 Advertising 패킷에 싣고, 나머지 상세 정보는 내부 GATT Server 메모리에 올립니다.
     */
    data class StartAdvertising(
        val wifiIp: String,
        val port: Int,
        val ssid: String,
        val pw: String
    ) : BluetoothEvent

    /** Stops broadcasting the BLE signal and closes the GATT Server. */
    data object StopAdvertising : BluetoothEvent

    /**
     * 스캔된 기기의 MAC 주소를 사용하여 GATT 연결을 맺고 데이터를 읽어옵니다.
     * (기존 HandshakeAndConnect(ip, port)를 대체함)
     */
    data class ConnectGatt(val macAddress: String) : BluetoothEvent
}