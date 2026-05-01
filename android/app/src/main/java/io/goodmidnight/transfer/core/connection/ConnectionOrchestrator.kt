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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central Facade and Orchestrator for all P2P network operations.
 * It abstracts the complexity of individual hardware controllers (NSD, BLE, Wi-Fi Direct)
 * and provides a unified interface for the UI layer.
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
        // Start continuously monitoring all underlying hardware controllers upon instantiation.
        observeStates()
        observeEffects()
        observeErrors()
    }

    /**
     * Routes incoming UI intents to the appropriate internal orchestration logic.
     */
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
                // Cache IP and Port for later use once connection is successful.
                updateState { copy(hostIp = event.hostIp, hostPort = 8080) }
                // Command physical connection to the specified Wi-Fi network.
                connectivityController.processEvent(
                    ConnectivityEvent.ConnectToHotspot(event.ssid, event.pw)
                )
            }
        }
    }

    /**
     * Initiates the device discovery process based on the requested protocol.
     * Ensures any previous discovery sessions are halted to prevent hardware collisions.
     */
    private suspend fun startDiscovery(mode: ConnectionMode) {
        stopAllDiscovery()

        // Reset the UI state for a fresh discovery session.
        updateState {
            copy(currentMode = mode, isDiscovering = true, discoveredPeers = emptyList())
        }

        when (mode) {
            ConnectionMode.NSD -> nsdController.processEvent(NsdEvent.StartDiscovery)
            ConnectionMode.WIFI_DIRECT -> wifiDirectController.processEvent(WifiDirectEvent.StartDiscovery)
            ConnectionMode.BLUETOOTH -> bluetoothController.processEvent(BluetoothEvent.StartScan)
            ConnectionMode.HOTSPOT -> hotSpotController.processEvent(HotSpotEvent.StartHotspot)
            ConnectionMode.AUTO -> nsdController.processEvent(NsdEvent.StartDiscovery)
        }
    }

    /**
     * Attempts to establish a socket-ready connection with the selected peer.
     * The behavior differs significantly depending on the underlying hardware protocol.
     */
    private suspend fun connectToPeer(peer: Peer) {
        stopAllDiscovery() // Conserve battery and network bandwidth during connection phase.

        when (peer.type) {
            Peer.Type.WIFI_DIRECT -> {
                // Wi-Fi Direct requires an active OS-level handshake to form a P2P Group.
                wifiDirectController.processEvent(WifiDirectEvent.ConnectToPeer(peer.address))
            }
            Peer.Type.NSD -> {
                // NSD resolves IP immediately, allowing instant connection establishment.
                updateState {
                    copy(
                        isConnected = true,
                        hostIp = peer.address,
                        hostPort = peer.port
                    )
                }
                emitEffect(
                    ConnectionEffect.ConnectionEstablished(
                        hostIp = peer.address,
                        port = peer.port,
                        type = peer.type
                    )
                )
            }
            Peer.Type.BLUETOOTH -> {
                // Bluetooth requires an initial GATT handshake using the MAC address to retrieve SSID/PW/IP.
                bluetoothController.processEvent(BluetoothEvent.ConnectGatt(peer.address))
            }
            else -> {
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
        stopAllDiscovery()
        stopHosting()
        updateState { copy(currentMode = mode, isHosting = true) }

        val currentIp = connectivityController.state.value.localIp

        when (mode) {
            ConnectionMode.NSD -> nsdController.processEvent(
                NsdEvent.StartBroadcasting(deviceName, port)
            )
            ConnectionMode.WIFI_DIRECT -> wifiDirectController.processEvent(WifiDirectEvent.CreateGroup)
            ConnectionMode.HOTSPOT -> hotSpotController.processEvent(HotSpotEvent.StartHotspot)
            ConnectionMode.BLUETOOTH -> {
                // Broadcast hotspot credentials via BLE Advertisement.
                if (wifiIp != null) {
                    bluetoothController.processEvent(
                        BluetoothEvent.StartAdvertising(
                            wifiIp = wifiIp,
                            port = port,
                            ssid = ssid ?: "",
                            pw = pw ?: ""
                        )
                    )
                } else {
                    emitError(ConnectionException.UnsupportedPeerTypeException(cause = Throwable("IP is required for BLE hosting")))
                }
            }
            ConnectionMode.AUTO -> {
                if (!currentIp.isNullOrBlank() && currentIp != "0.0.0.0") {
                    nsdController.processEvent(NsdEvent.StartBroadcasting(deviceName, port))
                    bluetoothController.processEvent(
                        BluetoothEvent.StartAdvertising(currentIp, port, ssid ?: "", pw ?: "")
                    )
                } else {
                    hotSpotController.processEvent(HotSpotEvent.StartHotspot)
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

    /** Safely halts all active hardware scanning processes. */
    private suspend fun stopAllDiscovery() {
        nsdController.processEvent(NsdEvent.StopDiscovery)
        wifiDirectController.processEvent(WifiDirectEvent.StopDiscovery)
        bluetoothController.processEvent(BluetoothEvent.StopScan)
        updateState { copy(isDiscovering = false) }
    }

    /** Completely tears down any active P2P groups, hotspots, and OS-level network bindings. */
    private suspend fun disconnectAll() {
        stopAllDiscovery()
        stopHosting()
        connectivityController.processEvent(ConnectivityEvent.ReleaseBinding)
        // Reset state entirely while preserving the mode if necessary
        updateState { ConnectionState() }
    }

    /**
     * Listens to the state flows of all child controllers and merges their discovered peers.
     */
    private fun observeStates() {
        controllerScope.launch {
            connectivityController.state.collect { connectivityState ->
                updateState { copy(localIp = connectivityState.localIp) }
            }
        }

        // Collect Discovered Peers from NSD (mDNS)
        controllerScope.launch {
            nsdController.state.collect { nsdState ->
                if (state.value.currentMode in listOf(ConnectionMode.NSD, ConnectionMode.AUTO)) {
                    updateState {
                        copy(discoveredPeers = (discoveredPeers + nsdState.discoveredPeers).distinctBy { it.address })
                    }
                }
            }
        }

        // Collect Discovered Peers from Wi-Fi Direct
        controllerScope.launch {
            wifiDirectController.state.collect { wfdState ->
                if (state.value.currentMode in listOf(ConnectionMode.WIFI_DIRECT, ConnectionMode.AUTO)) {
                    updateState {
                        copy(discoveredPeers = (discoveredPeers + wfdState.discoveredPeers).distinctBy { it.address })
                    }
                }
            }
        }

        // Collect Discovered Devices from Bluetooth Low Energy (BLE)
        controllerScope.launch {
            bluetoothController.state.collect { btState ->
                if (state.value.currentMode in listOf(ConnectionMode.BLUETOOTH, ConnectionMode.AUTO)) {
                    updateState {
                        copy(discoveredPeers = (discoveredPeers + btState.discoveredDevices).distinctBy { it.address })
                    }
                }
            }
        }

        // Collect HotSpot Status and trigger process binding
        controllerScope.launch {
            hotSpotController.state.collect { hotSpotState ->
                if (hotSpotState.isActive) {
                    updateState {
                        copy(
                            isConnected = true,
                            activeSsid = hotSpotState.ssid,
                            hostIp = ANDROID_HOTSPOT_GATEWAY_IP
                        )
                    }
                    connectivityController.processEvent(ConnectivityEvent.RequestBinding)

                    emitEffect(
                        ConnectionEffect.ConnectionEstablished(
                            hostIp = ANDROID_HOTSPOT_GATEWAY_IP,
                            port = DEFAULT_SOCKET_PORT,
                            type = Peer.Type.HOTSPOT
                        )
                    )
                }
            }
        }
    }

    /**
     * Centralized error handling. Propagates errors to the UI and rolls back temporary states.
     */
    private fun observeErrors() {
        val errorFlows = listOf(
            nsdController.error,
            wifiDirectController.error,
            hotSpotController.error,
            bluetoothController.error,
            connectivityController.error
        )

        errorFlows.forEach { errorFlow ->
            controllerScope.launch {
                errorFlow.collect { exception ->
                    emitError(exception)
                    // Rollback operational flags to prevent UI deadlock.
                    updateState {
                        copy(isDiscovering = false, isHosting = false, currentMode = ConnectionMode.AUTO)
                    }
                }
            }
        }
    }

    /**
     * Monitors asynchronous effects from controllers to establish the final socket connection.
     */
    private fun observeEffects() {
        // Collect successful connection effects from NSD
        controllerScope.launch {
            nsdController.effect.collect { effect ->
                if (effect is NsdEffect.ConnectionEstablished) {
                    emitEffect(
                        ConnectionEffect.ConnectionEstablished(
                            hostIp = effect.ipAddress,
                            port = effect.port,
                            type = Peer.Type.NSD
                        )
                    )
                }
            }
        }

        // Collect successful connection effects from Wi-Fi Direct
        controllerScope.launch {
            wifiDirectController.effect.collect { effect ->
                if (effect is WifiDirectEffect.ConnectionEstablished) {
                    updateState { copy(isConnected = true, hostIp = effect.groupOwnerIp) }
                    connectivityController.processEvent(ConnectivityEvent.RequestBinding)

                    emitEffect(
                        ConnectionEffect.ConnectionEstablished(
                            hostIp = effect.groupOwnerIp,
                            port = DEFAULT_SOCKET_PORT,
                            type = Peer.Type.WIFI_DIRECT
                        )
                    )
                }
            }
        }

        // Collect successful connection effects from Bluetooth
        controllerScope.launch {
            bluetoothController.effect.collect { effect ->
                if (effect is BluetoothEffect.HandshakeCompleted) {
                    updateState { copy(hostIp = effect.ip, hostPort = effect.port) }

                    if (effect.ssid.isNotEmpty() && effect.pw.isNotEmpty()) {
                        // rigger auto-connection using retrieved Hotspot credentials
                        connectivityController.processEvent(
                            ConnectivityEvent.ConnectToHotspot(effect.ssid, effect.pw)
                        )
                    } else {
                        // No Hotspot credentials (same Wi-Fi), proceed directly to transfer
                        updateState { copy(isConnected = true) }
                        emitEffect(
                            ConnectionEffect.ConnectionEstablished(
                                hostIp = effect.ip,
                                port = effect.port,
                                type = Peer.Type.BLUETOOTH
                            )
                        )
                    }
                }
            }
        }

        // Collect successful connection effects from Connectivity (Hotspot Binding)
        controllerScope.launch {
            connectivityController.effect.collect { effect ->
                if (effect is ConnectivityEffect.HotspotConnectedAndBound) {
                    val ip = state.value.hostIp ?: return@collect
                    val port = state.value.hostPort

                    updateState { copy(isConnected = true) }
                    emitEffect(ConnectionEffect.ConnectionEstablished(ip, port, Peer.Type.HOTSPOT))
                }
            }
        }
    }

    companion object {

        /**
         * Android OS defaults the LocalOnlyHotspot gateway IP to 192.168.43.1.
         * Clients connecting to this device's hotspot must send socket data to this IP.
         */
        private const val ANDROID_HOTSPOT_GATEWAY_IP = "192.168.43.1"

        /**
         * Default port for file transfer sockets.
         */
        private const val DEFAULT_SOCKET_PORT = 8080
    }
}