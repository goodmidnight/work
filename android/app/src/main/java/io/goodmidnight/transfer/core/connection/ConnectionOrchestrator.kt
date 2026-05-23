package io.goodmidnight.transfer.core.connection

import android.util.Log
import io.goodmidnight.transfer.core.connection.ConnectionState.ConnectionMode
import io.goodmidnight.transfer.core.connection.bluetooth.BluetoothController
import io.goodmidnight.transfer.core.connection.bluetooth.BluetoothEffect
import io.goodmidnight.transfer.core.connection.bluetooth.BluetoothEvent
import io.goodmidnight.transfer.core.connection.connectivity.ConnectivityController
import io.goodmidnight.transfer.core.connection.connectivity.ConnectivityEffect
import io.goodmidnight.transfer.core.connection.connectivity.ConnectivityEvent
import io.goodmidnight.transfer.core.connection.hotspot.HotSpotController
import io.goodmidnight.transfer.core.connection.hotspot.HotSpotEvent
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.connection.nsd.NsdController
import io.goodmidnight.transfer.core.connection.nsd.NsdEffect
import io.goodmidnight.transfer.core.connection.nsd.NsdEvent
import io.goodmidnight.transfer.core.connection.wifidirect.WifiDirectController
import io.goodmidnight.transfer.core.connection.wifidirect.WifiDirectEffect
import io.goodmidnight.transfer.core.connection.wifidirect.WifiDirectEvent
import io.goodmidnight.transfer.core.base.BaseController
import io.goodmidnight.transfer.core.exception.ApplicationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central Facade and Orchestrator for all P2P network operations.
 */
@Singleton
class ConnectionOrchestrator @Inject constructor(
    private val nsdController: NsdController,
    private val wifiDirectController: WifiDirectController,
    private val bluetoothController: BluetoothController,
    private val hotSpotController: HotSpotController,
    private val connectivityController: ConnectivityController,
) : BaseController<ConnectionState, ConnectionEvent, ConnectionEffect, ApplicationException>(
    controllerName = "ConnectionOrchestrator",
    initialState = ConnectionState()
) {

    init {
        observeStates()
        observeEffects()
        observeErrors()
    }

    override suspend fun handleEvent(event: ConnectionEvent) {
        when (event) {
            is ConnectionEvent.StartDiscovery -> startDiscovery(event.mode)
            is ConnectionEvent.StopDiscovery -> stopAllDiscovery()
            is ConnectionEvent.ConnectToPeer -> connectToPeer(event.peer)
            is ConnectionEvent.Disconnect -> disconnectAll()
            is ConnectionEvent.StartHosting -> startHosting(
                mode = event.mode,
                deviceName = event.deviceName,
                wifiIp = event.wifiIp,
                port = event.port,
                ssid = event.ssid,
                pw = event.pw
            )
            is ConnectionEvent.StopHosting -> stopHosting()
            is ConnectionEvent.ConnectToHotspot -> {
                Log.i(TAG, "Connecting to hotspot: ${event.ssid}")
                updateState { copy(hostIp = event.hostIp, hostPort = DEFAULT_SOCKET_PORT) }
                connectivityController.processEvent(
                    ConnectivityEvent.ConnectToHotspot(event.ssid, event.pw)
                )
            }
        }
    }

    private suspend fun startDiscovery(mode: ConnectionMode) {
        Log.i(TAG, "Starting discovery in mode: $mode")
        stopAllDiscovery()

        updateState {
            copy(currentMode = mode, isDiscovering = true, discoveredPeers = emptyList())
        }

        when (mode) {
            ConnectionMode.NSD -> nsdController.processEvent(NsdEvent.StartDiscovery)
            ConnectionMode.WIFI_DIRECT -> wifiDirectController.processEvent(WifiDirectEvent.StartDiscovery)
            ConnectionMode.BLUETOOTH -> bluetoothController.processEvent(BluetoothEvent.StartScan)
            ConnectionMode.HOTSPOT -> hotSpotController.processEvent(HotSpotEvent.StartHotspot)
            ConnectionMode.AUTO -> {
                Log.d(TAG, "AUTO mode: Starting NSD, BLE, and WiFi-Direct")
                nsdController.processEvent(NsdEvent.StartDiscovery)
                bluetoothController.processEvent(BluetoothEvent.StartScan)
                wifiDirectController.processEvent(WifiDirectEvent.StartDiscovery)
            }
        }
    }

    private suspend fun connectToPeer(peer: Peer) {
        Log.i(TAG, "Attempting to connect to peer: ${peer.deviceName} (${peer.type})")
        stopAllDiscovery()

        when (peer.type) {
            Peer.Type.WIFI_DIRECT -> {
                wifiDirectController.processEvent(WifiDirectEvent.ConnectToPeer(peer.address))
            }
            Peer.Type.NSD -> {
                updateState { copy(isConnected = true, hostIp = peer.address, hostPort = peer.port) }
                emitEffect(ConnectionEffect.ConnectionEstablished(peer.address, peer.port, peer.type))
            }
            Peer.Type.BLUETOOTH -> {
                bluetoothController.processEvent(BluetoothEvent.ConnectGatt(peer.address))
            }
            else -> {
                Log.e(TAG, "Unsupported peer type: ${peer.type}")
                emitError(ConnectionException.UnsupportedPeerTypeException())
            }
        }
    }

    private suspend fun startHosting(
        mode: ConnectionMode,
        deviceName: String,
        wifiIp: String?,
        port: Int,
        ssid: String?,
        pw: String?,
    ) {
        Log.i(TAG, "Starting hosting: mode=$mode, deviceName=$deviceName")
        stopAllDiscovery()
        stopHosting()
        updateState { copy(currentMode = mode, isHosting = true, hostPort = port) }

        val currentIp = connectivityController.state.value.localIp

        when (mode) {
            ConnectionMode.NSD -> nsdController.processEvent(NsdEvent.StartBroadcasting(deviceName, port))
            ConnectionMode.WIFI_DIRECT -> wifiDirectController.processEvent(WifiDirectEvent.CreateGroup)
            ConnectionMode.HOTSPOT -> hotSpotController.processEvent(HotSpotEvent.StartHotspot)
            ConnectionMode.BLUETOOTH -> {
                if (wifiIp != null) {
                    bluetoothController.processEvent(BluetoothEvent.StartAdvertising(wifiIp, port, ssid ?: "", pw ?: ""))
                } else {
                    emitError(ConnectionException.UnsupportedPeerTypeException(cause = Throwable("IP is required for BLE hosting")))
                }
            }
            ConnectionMode.AUTO -> {
                Log.d(TAG, "AUTO mode hosting: Broadcasting on ALL protocols")
                wifiDirectController.processEvent(WifiDirectEvent.CreateGroup)
                if (!currentIp.isNullOrBlank() && currentIp != "0.0.0.0") {
                    nsdController.processEvent(NsdEvent.StartBroadcasting(deviceName, port))
                    bluetoothController.processEvent(BluetoothEvent.StartAdvertising(currentIp, port, ssid ?: "", pw ?: ""))
                } else {
                    hotSpotController.processEvent(HotSpotEvent.StartHotspot)
                    bluetoothController.processEvent(BluetoothEvent.StartAdvertising(ANDROID_HOTSPOT_GATEWAY_IP, port, ssid ?: "", pw ?: ""))
                }
            }
        }
    }

    private suspend fun stopHosting() {
        nsdController.processEvent(NsdEvent.StopBroadcasting)
        wifiDirectController.processEvent(WifiDirectEvent.RemoveGroup)
        hotSpotController.processEvent(HotSpotEvent.StopHotspot)
        bluetoothController.processEvent(BluetoothEvent.StopAdvertising)
        updateState { copy(isHosting = false, activeSsid = null) }
    }

    private suspend fun stopAllDiscovery() {
        nsdController.processEvent(NsdEvent.StopDiscovery)
        wifiDirectController.processEvent(WifiDirectEvent.StopDiscovery)
        bluetoothController.processEvent(BluetoothEvent.StopScan)
        updateState { copy(isDiscovering = false) }
    }

    private suspend fun disconnectAll() {
        stopAllDiscovery()
        stopHosting()
        connectivityController.processEvent(ConnectivityEvent.ReleaseBinding)
        updateState { ConnectionState() }
    }

    private fun observeStates() {
        controllerScope.launch {
            connectivityController.state.collect { connectivityState ->
                updateState { copy(localIp = connectivityState.localIp) }
            }
        }

        controllerScope.launch {
            combine(
                nsdController.state,
                wifiDirectController.state,
                bluetoothController.state
            ) { nsd, wfd, bt ->
                Triple(nsd.discoveredPeers, wfd.discoveredPeers, bt.discoveredDevices)
            }.collect { (nsdPeers, wfdPeers, btPeers) ->
                val allPeers = (nsdPeers + wfdPeers + btPeers).distinctBy { 
                    if (it.type == Peer.Type.BLUETOOTH) it.address else "${it.address}:${it.port}"
                }
                Log.v(TAG, "Merged Peers update: NSD:${nsdPeers.size}, WFD:${wfdPeers.size}, BT:${btPeers.size}")
                updateState { copy(discoveredPeers = allPeers) }
            }
        }

        controllerScope.launch {
            hotSpotController.state.collect { hotSpotState ->
                if (hotSpotState.isActive) {
                    updateState { copy(isConnected = true, activeSsid = hotSpotState.ssid, hostIp = ANDROID_HOTSPOT_GATEWAY_IP) }
                    connectivityController.processEvent(ConnectivityEvent.RequestBinding)
                }
            }
        }
    }

    private fun observeEffects() {
        controllerScope.launch {
            nsdController.effect.collect { effect ->
                if (effect is NsdEffect.ConnectionEstablished) {
                    updateState { copy(isConnected = true, hostIp = effect.ipAddress, hostPort = effect.port) }
                    emitEffect(ConnectionEffect.ConnectionEstablished(effect.ipAddress, effect.port, Peer.Type.NSD))
                }
            }
        }

        controllerScope.launch {
            bluetoothController.effect.collect { effect ->
                if (effect is BluetoothEffect.HandshakeCompleted) {
                    if (effect.ssid.isNotEmpty()) {
                        processEvent(ConnectionEvent.ConnectToHotspot(effect.ssid, effect.pw, effect.ip))
                    } else {
                        updateState { copy(isConnected = true, hostIp = effect.ip, hostPort = effect.port) }
                        emitEffect(ConnectionEffect.ConnectionEstablished(effect.ip, effect.port, Peer.Type.BLUETOOTH))
                    }
                }
            }
        }

        controllerScope.launch {
            wifiDirectController.effect.collect { effect ->
                if (effect is WifiDirectEffect.ConnectionEstablished) {
                    updateState { copy(isConnected = true, hostIp = effect.groupOwnerIp, hostPort = DEFAULT_SOCKET_PORT) }
                    connectivityController.processEvent(ConnectivityEvent.RequestBinding)
                    if (!state.value.isHosting) {
                        emitEffect(ConnectionEffect.ConnectionEstablished(effect.groupOwnerIp, DEFAULT_SOCKET_PORT, Peer.Type.WIFI_DIRECT))
                    }
                }
            }
        }

        controllerScope.launch {
            connectivityController.effect.collect { effect ->
                when (effect) {
                    is ConnectivityEffect.BindingEstablished -> {
                        val hostIp = state.value.hostIp ?: effect.localIp
                        if (hostIp != null && !state.value.isHosting) {
                            emitEffect(ConnectionEffect.ConnectionEstablished(hostIp, state.value.hostPort, Peer.Type.HOTSPOT))
                        }
                    }
                    is ConnectivityEffect.HotspotConnectedAndBound -> {
                        val hostIp = state.value.hostIp ?: effect.localIp
                        if (hostIp != null && !state.value.isHosting) {
                            emitEffect(ConnectionEffect.ConnectionEstablished(hostIp, state.value.hostPort, Peer.Type.HOTSPOT))
                        }
                    }
                    is ConnectivityEffect.NetworkLost -> {
                        updateState { copy(isConnected = false) }
                        emitEffect(ConnectionEffect.ConnectionLost(Peer.Type.HOTSPOT))
                    }
                }
            }
        }
    }

    private fun observeErrors() {
        controllerScope.launch {
            nsdController.error.collect { error ->
                emitError(ConnectionException.FailedConnectionException(error.message ?: "NSD Error", error))
            }
        }
        controllerScope.launch {
            bluetoothController.error.collect { error ->
                emitError(ConnectionException.FailedConnectionException(error.message ?: "Bluetooth Error", error))
            }
        }
        controllerScope.launch {
            wifiDirectController.error.collect { error ->
                emitError(ConnectionException.FailedConnectionException(error.message ?: "WifiDirect Error", error))
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionOrchestrator"
        private const val ANDROID_HOTSPOT_GATEWAY_IP = "192.168.43.1"
        private const val DEFAULT_SOCKET_PORT = 50001
    }
}
