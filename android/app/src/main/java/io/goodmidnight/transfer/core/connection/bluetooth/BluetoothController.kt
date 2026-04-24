package io.goodmidnight.transfer.core.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.exception.ApplicationException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

/**
 * Controller responsible for Bluetooth Low Energy (BLE) operations.
 * It handles both scanning for peers (Senders) and advertising IP info (Receivers).
 * Note: Permissions (BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, etc.) must be granted by the UI/ViewModel layer before using this controller.
 */
@SuppressLint("MissingPermission")
class BluetoothController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("BluetoothController"))

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    // Unique Service UUID specifically designed for this Handshake protocol.
    val SERVICE_UUID: UUID = UUID.fromString("13f8c85e-5c4f-4d9a-8e2b-3a5c1d4e7f9a")

    private val _state = MutableStateFlow(BluetoothState())
    val state = _state.asStateFlow()

    private val _event = Channel<BluetoothEvent>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    private val _effect = MutableSharedFlow<BluetoothEffect>()
    val effect = _effect.asSharedFlow()

    init {
        observeEvents()
    }

    /**
     * Exposes a thread-safe method to send user intents or system commands to this controller.
     */
    suspend fun processEvent(event: BluetoothEvent) = _event.send(event)

    /**
     * Observes incoming events and routes them to the appropriate handler.
     */
    private fun observeEvents() {
        scope.launch {
            _event.receiveAsFlow().collect { event ->
                when (event) {
                    is BluetoothEvent.StartScan -> handleStartScan()
                    is BluetoothEvent.StopScan -> handleStopScan()
                    is BluetoothEvent.StartAdvertising -> handleStartAdvertising(
                        event.wifiIp,
                        event.port
                    )

                    is BluetoothEvent.StopAdvertising -> handleStopAdvertising()
                    is BluetoothEvent.HandshakeAndConnect -> handleHandshake(
                        event.ipAddress,
                        event.port
                    )
                }
            }
        }
    }

    /**
     * Starts broadcasting this device's Wi-Fi IP and Port via BLE so senders can discover it.
     */
    private fun handleStartAdvertising(ip: String, port: Int) {
        if (advertiser == null) {
            emitError(BluetoothException.AdvertiseException(message = "BluetoothLeAdvertiser is not available on this device."))
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .build()

        val serviceData: ByteArray = try {
            // Serialize the IP and Port into a compact 6-byte array to fit within the BLE payload limit
            BleDataSerializer.serialize(ip, port)
        } catch (e: Exception) {
            emitError(
                BluetoothException.AdvertiseException(
                    message = "Failed to serialize IP data",
                    cause = e
                )
            )
            return
        }

        val parcelUuid = ParcelUuid(SERVICE_UUID)
        val data = AdvertiseData.Builder()
            .addServiceUuid(parcelUuid)
            .addServiceData(parcelUuid, serviceData) // Attach the serialized IP/Port data
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        _state.update { it.copy(isAdvertising = true) }
    }

    /**
     * Starts scanning for nearby BLE devices that are broadcasting our specific Service UUID.
     */
    private fun handleStartScan() {
        if (scanner == null) {
            emitError(BluetoothException.ScanException(message = "BluetoothLeScanner is not available on this device."))
            return
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            _state.update { it.copy(isScanning = true) }
        } catch (e: Exception) {
            emitError(BluetoothException.ScanException(cause = e))
        }
    }

    /**
     * Callback invoked by the OS when a nearby device matching our UUID filter is found.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: "Unknown Device"
            val scanRecord = result.scanRecord ?: return

            // Extract the 6-byte Service Data mapped to our specific UUID
            val parcelUuid = ParcelUuid(SERVICE_UUID)
            val serviceData = scanRecord.serviceData[parcelUuid]

            if (serviceData != null && serviceData.size == 6) {
                try {
                    // Deserialize the compact bytes back into an IP string and Port integer
                    val (ip, port) = BleDataSerializer.deserialize(serviceData)

                    // Create a Peer domain model with the decoded information
                    val peer = Peer(deviceName, ip, port, Peer.Type.BLUETOOTH)

                    // Update the state securely. Append only if the peer is not already in the list to prevent duplicates.
                    _state.update { state ->
                        if (state.discoveredDevices.none { it.address == peer.address && it.deviceName == peer.deviceName }) {
                            state.copy(discoveredDevices = state.discoveredDevices + peer)
                        } else state
                    }
                } catch (e: Exception) {
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            emitError(BluetoothException.ScanException(message = "BLE Scan failed with error code: $errorCode"))
            _state.update { it.copy(isScanning = false) }
        }
    }

    /**
     * Callback invoked by the OS to report the status of BLE advertising.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // Advertising started successfully. State is already updated to true in handleStartAdvertising.
        }

        override fun onStartFailure(errorCode: Int) {
            emitError(BluetoothException.AdvertiseException(message = "BLE Advertising failed with error code: $errorCode"))
            _state.update { it.copy(isAdvertising = false) }
        }
    }

    /**
     * Emits an effect indicating that the BLE handshake has successfully resolved the target IP and Port.
     * This prompts the ConnectionManager to initiate the actual Wi-Fi socket connection.
     */
    private fun handleHandshake(ip: String, port: Int) {
        scope.launch { _effect.emit(BluetoothEffect.ConnectionEstablished(ip, port)) }
    }

    /**
     * Halts the BLE scanning process.
     */
    private fun handleStopScan() {
        scanner?.stopScan(scanCallback)
        _state.update { it.copy(isScanning = false) }
    }

    /**
     * Halts the BLE advertising process.
     */
    private fun handleStopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        _state.update { it.copy(isAdvertising = false) }
    }

    /**
     * Helper method to dispatch exceptions securely to the shared effect flow.
     */
    private fun emitError(exception: ApplicationException) {
        scope.launch {
            _effect.emit(BluetoothEffect.SendError(exception))
        }
    }
}