package io.goodmidnight.transfer.core.connection.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.exception.ApplicationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Controller responsible for managing Wi-Fi Direct (P2P) connections.
 * Wi-Fi Direct allows devices to connect directly via Wi-Fi without an intermediate access point.
 * Note: Requires location and nearby devices permissions to function properly.
 */
@SuppressLint("MissingPermission")
class WifiDirectController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("WifiDirectController"))
    private val manager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = manager.initialize(context, context.mainLooper, null)

    private val _state = MutableStateFlow(WifiDirectState())
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val _event = Channel<WifiDirectEvent>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    private val _effect = MutableSharedFlow<WifiDirectEffect>()
    val effect = _effect.asSharedFlow()

    init {
        observeEvents()
        registerWifiDirectReceiver()
    }

    suspend fun processEvent(event: WifiDirectEvent) = _event.send(event)

    private fun observeEvents() {
        scope.launch {
            _event.receiveAsFlow().collect { event ->
                when (event) {
                    is WifiDirectEvent.StartDiscovery -> handleStartDiscovery()
                    is WifiDirectEvent.StopDiscovery -> handleStopDiscovery()
                    is WifiDirectEvent.CreateGroup -> handleCreateGroup()
                    is WifiDirectEvent.RemoveGroup -> handleRemoveGroup()
                    is WifiDirectEvent.ConnectToPeer -> handleConnect(event.deviceAddress)
                }
            }
        }
    }


    /**
     * Initiates peer discovery.
     * Note: This only starts the scanning process. The actual peer list is delivered
     * asynchronously via the BroadcastReceiver when WIFI_P2P_PEERS_CHANGED_ACTION is fired.
     */
    private fun handleStartDiscovery() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.update { it.copy(isDiscovering = true) }
            }
            override fun onFailure(reason: Int) {
                emitError(WifiDirectException.DiscoveryException(message = "Discovery failed: Reason code $reason"))
                _state.update { it.copy(isDiscovering = false) }
            }
        })
    }

    /**
     * Requests the current list of discovered peers from the Wi-Fi P2P Manager.
     * This should be called when the system notifies us that the peer list has changed.
     */
    private fun requestPeerList() {
        manager.requestPeers(channel) { peers ->
            val peerList = peers.deviceList.map { device ->
                val deviceName = device.deviceName ?: "Unknown Device"
                val macAddress = device.deviceAddress ?: ""
                Peer(deviceName, macAddress, 8080, Peer.Type.WIFI_DIRECT)
            }
            _state.update { it.copy(discoveredPeers = peerList) }
        }
    }

    /**
     * Attempts to connect to a specific peer using their MAC address.
     */
    private fun handleConnect(address: String) {
        val config = WifiP2pConfig().apply { deviceAddress = address }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Connection initiated successfully.
                // Actual connection confirmation comes via BroadcastReceiver.
            }
            override fun onFailure(reason: Int) {
                emitError(WifiDirectException.ConnectionException(message = "Connection failed: Reason code $reason"))
            }
        })
    }

    /** Fetches the connection info to determine the Group Owner IP after a successful connection. */
    private fun requestInfo() {
        manager.requestConnectionInfo(channel) { info ->
            if (info.groupFormed) {
                val hostIp = info.groupOwnerAddress?.hostAddress ?: ""
                _state.update { it.copy(isConnected = true, isGroupOwner = info.isGroupOwner) }
                scope.launch { _effect.emit(WifiDirectEffect.ConnectionEstablished(hostIp)) }
            }
        }
    }

    /**
     *  Creates a P2P Group, making this device the Group Owner (Hub).
     */
    private fun handleCreateGroup() {
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.update { it.copy(isGroupOwner = true) }
            }
            override fun onFailure(reason: Int) {
                emitError(WifiDirectException.GroupException(message = "Group creation failed: Reason code $reason"))
            }
        })
    }

    /**
     *  Tears down the P2P Group and disconnects.
     */
    private fun handleRemoveGroup() {
        manager.removeGroup(channel, null)
        _state.update { it.copy(isConnected = false, isGroupOwner = false, discoveredPeers = emptyList()) }
    }

    /**
     * Halts the discovery process.
     */
    private fun handleStopDiscovery() {
        manager.stopPeerDiscovery(channel, null)
        _state.update { it.copy(isDiscovering = false) }
    }

    private fun emitError(exception: ApplicationException) {
        scope.launch { _effect.emit(WifiDirectEffect.SendError(exception)) }
    }

    /**
     * Registers a BroadcastReceiver to listen for asynchronous Wi-Fi Direct events
     * dispatched by the Android OS.
     */
    private fun registerWifiDirectReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (_state.value.isDiscovering) {
                            requestPeerList()
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            requestInfo()
                        } else {
                            _state.update { it.copy(isConnected = false, isGroupOwner = false) }
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)
    }
}