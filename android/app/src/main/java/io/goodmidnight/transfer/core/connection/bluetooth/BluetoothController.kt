package io.goodmidnight.transfer.core.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.base.BaseController
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Bluetooth LE Controller for exchanging initial connection (Handshake) information for P2P file transfer.
 * The Host opens a GATT server to provide connection details, and the Client scans to read them.
 */
@SuppressLint("MissingPermission")
class BluetoothController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<BluetoothState, BluetoothEvent, BluetoothEffect, BluetoothException>(
    "BluetoothController",
    BluetoothState()
) {
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    // GATT Server (for data provider/Host) and Client (for data consumer/Scanner)
    private var gattServer: BluetoothGattServer? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Cached JSON payload containing Wi-Fi/Hotspot info to be provided by the host
    private var hostingPayload: ByteArray = ByteArray(0)

    companion object {
        // Unique UUIDs for app identification, used in scan filters and service/characteristic definition.
        val SERVICE_UUID: UUID = UUID.fromString("13f8c85e-5c4f-4d9a-8e2b-3a5c1d4e7f9a")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("24a9c85e-5c4f-4d9a-8e2b-3a5c1d4e7f9b")
    }

    override suspend fun handleEvent(event: BluetoothEvent) {
        when (event) {
            is BluetoothEvent.StartScan -> handleStartScan()
            is BluetoothEvent.StopScan -> handleStopScan()
            is BluetoothEvent.StartAdvertising -> handleStartAdvertising(
                event.wifiIp, event.port, event.ssid, event.pw
            )

            is BluetoothEvent.StopAdvertising -> handleStopAdvertising()
            is BluetoothEvent.ConnectGatt -> handleConnectGatt(event.macAddress)
        }
    }

    private fun handleStartAdvertising(ip: String, port: Int, ssid: String, pw: String) {
        if (advertiser == null) {
            emitError(BluetoothException.AdvertiseException(message = "Bluetooth Advertiser is not supported on this device."))
            return
        }

        // Serialize large payload into JSON and cache in memory
        val jsonString = JSONObject().apply {
            put("ip", ip)
            put("port", port)
            put("ssid", ssid)
            put("pw", pw)
        }.toString()
        hostingPayload = jsonString.toByteArray(Charsets.UTF_8)

        gattServer = btManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        // Configure Advertising packet (Broadcast only the app-specific UUID without payload)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        //  Start Advertising using runCatching for functional error handling
        runCatching {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        }.onSuccess {
            updateState { copy(isAdvertising = true) }
        }.onFailure { e ->
            emitError(
                BluetoothException.AdvertiseException(
                    message = "Failed to start advertising",
                    cause = e
                )
            )
        }
    }

    /**
     * Callback responding to data requests from connected clients.
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                // Send the remaining data starting from the requested offset.
                // Since the client requests MTU(512), the entire payload is likely sent in one response.
                val valueToRead = if (offset > hostingPayload.size) ByteArray(0)
                else hostingPayload.copyOfRange(offset, hostingPayload.size)

                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    valueToRead
                )
            } else {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset,
                    ByteArray(0)
                )
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            updateState { copy(isAdvertising = false) }
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
    }

    private fun handleStartScan() {
        if (scanner == null) return

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        runCatching {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        }.onSuccess {
            updateState { copy(isScanning = true) }
        }.onFailure { e ->
            emitError(BluetoothException.ScanException(message = "Failed to start scan", cause = e))
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val peer = Peer(
                deviceName = device.name ?: "Unknown Device",
                address = device.address,
                port = 0, // Exact port is unknown at this stage
                type = Peer.Type.BLUETOOTH
            )

            updateState {
                copy(discoveredDevices = (discoveredDevices + peer).distinctBy { it.address })
            }
        }
    }

    private fun handleConnectGatt(macAddress: String) {
        val device = adapter?.getRemoteDevice(macAddress)
        if (device == null) {
            emitError(BluetoothException.GattConnectionException(message = "Device not found."))
            return
        }

        handleStopScan() // Stop scanning immediately to improve connection stability
        bluetoothGatt = device.connectGatt(context, false, gattClientCallback)
    }

    /**
     * Asynchronous pipeline for 1:1 connection with the Host to fetch data.
     */
    private val gattClientCallback = object : BluetoothGattCallback() {

        //  Physical connection state changed
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Request bandwidth expansion to 512 bytes to bypass the default 20-byte limit
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
            }
        }

        // Service discovery starts after MTU expansion is approved
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.discoverServices()
            }
        }

        // Request data read after services are discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val characteristic =
                    gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                characteristic?.let { gatt.readCharacteristic(it) }
            }
        }

        // Successfully received data (JSON) from the Host
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runCatching {
                    val jsonString = String(characteristic.value, Charsets.UTF_8)
                    val json = JSONObject(jsonString)

                    val ip = json.getString("ip")
                    val port = json.getInt("port")
                    val ssid = json.optString("ssid", "")
                    val pw = json.optString("pw", "")

                    BluetoothEffect.HandshakeCompleted(ip, port, ssid, pw)
                }.onSuccess { effect ->
                    emitEffect(effect)
                }.onFailure { e ->
                    emitError(
                        BluetoothException.GattConnectionException(
                            message = "Failed to parse BLE data",
                            cause = e
                        )
                    )
                }.also {
                    gatt.disconnect()
                }
            }
        }
    }

    private fun handleStopScan() {
        runCatching {
            scanner?.stopScan(scanCallback)
        }.onFailure { e ->
            Log.w("BluetoothController", "Security exception during stopScan: $e")
        }
        updateState { copy(isScanning = false) }
    }

    private fun handleStopAdvertising() {
        runCatching {
            advertiser?.stopAdvertising(advertiseCallback)
        }.onFailure { e ->
            Log.w("BluetoothController", "Security exception during stopAdvertising: $e")
        }
        gattServer?.clearServices()
        gattServer?.close()
        updateState { copy(isAdvertising = false) }
    }

    override fun clear() {
        handleStopScan()
        handleStopAdvertising()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        super.clear()
    }
}