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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.base.BaseController
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Bluetooth LE Controller for exchanging initial connection (Handshake) information.
 * Handles both Scanning (Client) and Advertising (Host).
 */
@SuppressLint("MissingPermission")
class BluetoothController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<BluetoothState, BluetoothEvent, BluetoothEffect, BluetoothException>(
    "BluetoothController",
    BluetoothState()
) {
    private val btManager: BluetoothManager by lazy { 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager 
    }
    
    // Always get the latest instances from the adapter to handle BT toggle (ON/OFF)
    private val adapter: BluetoothAdapter? get() = btManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var hostingPayload: ByteArray = ByteArray(0)

    companion object {
        private const val TAG = "BluetoothController"
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
        Log.i(TAG, "Starting Advertising: IP=$ip, Port=$port")
        val currentAdvertiser = advertiser
        if (currentAdvertiser == null) {
            Log.e(TAG, "Bluetooth Advertiser not supported or BT is OFF")
            emitError(BluetoothException.AdvertiseException(message = "Bluetooth Advertiser not supported"))
            return
        }

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

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Critical: Put UUID in the primary packet to ensure discovery
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeTxPowerLevel(false)
            .build()

        // Put device name in the scan response to save space in the main packet
        val scanResponseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        runCatching {
            currentAdvertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
        }.onSuccess {
            updateState { copy(isAdvertising = true) }
        }.onFailure { e ->
            Log.e(TAG, "Failed to start advertising", e)
            emitError(BluetoothException.AdvertiseException(message = "StartAdvertising failed", cause = e))
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            Log.d(TAG, "GATT Read Request from ${device.address}")
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val valueToRead = if (offset >= hostingPayload.size) ByteArray(0)
                else hostingPayload.copyOfRange(offset, hostingPayload.size)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, valueToRead)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, ByteArray(0))
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertise start failed: $errorCode")
            updateState { copy(isAdvertising = false) }
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "!!! BLE ADVERTISE STARTED SUCCESSFULLY !!!")
        }
    }

    private fun handleStartScan() {
        Log.i(TAG, "Starting BLE Scan")
        val currentScanner = scanner
        if (currentScanner == null) {
            Log.w(TAG, "Scanner is null - Bluetooth might be OFF")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching {
            currentScanner.startScan(null, settings, scanCallback)
        }.onSuccess {
            updateState { copy(isScanning = true) }
        }.onFailure { e ->
            Log.e(TAG, "Failed to start scan", e)
            emitError(BluetoothException.ScanException(message = "StartScan failed", cause = e))
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            
            val serviceUuids = scanRecord?.serviceUuids
            val isOurDevice = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true

            if (!isOurDevice) return

            val rawName = scanRecord.deviceName ?: device.name
            val displayName = if (!rawName.isNullOrBlank()) {
                rawName
            } else {
                "전송 대기 중 (${device.address.takeLast(5)})"
            }

            Log.i(TAG, "MATCH! Found target host: $displayName (${device.address})")


            val peer = Peer(
                deviceName = displayName,
                address = device.address,
                port = 0,
                type = Peer.Type.BLUETOOTH
            )

            updateState {
                copy(discoveredDevices = (discoveredDevices + peer).distinctBy { it.address })
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
        }
    }

    private fun handleConnectGatt(macAddress: String) {
        Log.i(TAG, "Connecting GATT to $macAddress")
        val device = adapter?.getRemoteDevice(macAddress)
        if (device == null) {
            emitError(BluetoothException.GattConnectionException(message = "Device not found"))
            return
        }

        handleStopScan()
        bluetoothGatt = device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT Error status: $status. closing.")
                gatt.close()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT Connected, requesting MTU")
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT Disconnected")
                gatt.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU updated to $mtu, discovering services")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic)
                } else {
                    Log.e(TAG, "Handshake characteristic not found")
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            processReadData(value, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            processReadData(characteristic.value ?: ByteArray(0), status)
        }

        private fun processReadData(value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runCatching {
                    val jsonString = String(value, Charsets.UTF_8)
                    Log.i(TAG, "Received Handshake: $jsonString")
                    val json = JSONObject(jsonString)
                    BluetoothEffect.HandshakeCompleted(
                        json.getString("ip"),
                        json.getInt("port"),
                        json.optString("ssid", ""),
                        json.optString("pw", "")
                    )
                }.onSuccess { effect ->
                    emitEffect(effect)
                }.onFailure { e ->
                    Log.e(TAG, "Data parse failed", e)
                }.also {
                    bluetoothGatt?.disconnect()
                }
            }
        }
    }

    private fun handleStopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        updateState { copy(isScanning = false) }
    }

    private fun handleStopAdvertising() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
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
