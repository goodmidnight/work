package io.goodmidnight.transfer.core.connection.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.base.BaseController
import javax.inject.Inject

/**
 * Controller responsible for managing Wi-Fi Direct (P2P) connections.
 * Wi-Fi Direct allows devices to connect directly via Wi-Fi without an intermediate access point.
 * Note: Requires Location and Nearby Devices permissions to function properly.
 */
@SuppressLint("MissingPermission")
class WifiDirectController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<WifiDirectState, WifiDirectEvent, WifiDirectEffect, WifiDirectException>(
    "WifiDirectController",
    WifiDirectState()
) {
    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(context, context.mainLooper, null)

    private var wifiDirectReceiver: BroadcastReceiver? = null

    init {
        registerWifiDirectReceiver()
    }

    override suspend fun handleEvent(event: WifiDirectEvent) {
        when (event) {
            is WifiDirectEvent.StartDiscovery -> handleStartDiscovery()
            is WifiDirectEvent.StopDiscovery -> handleStopDiscovery()
            is WifiDirectEvent.CreateGroup -> handleCreateGroup()
            is WifiDirectEvent.RemoveGroup -> handleRemoveGroup()
            is WifiDirectEvent.ConnectToPeer -> handleConnect(event.deviceAddress)
        }
    }

    /**
     * Initiates peer discovery.
     * Note: This only starts the scanning process. The actual peer list is delivered
     * asynchronously via the BroadcastReceiver when WIFI_P2P_PEERS_CHANGED_ACTION is fired.
     */
    private fun handleStartDiscovery() {
        runCatching {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    updateState { copy(isDiscovering = true) }
                }

                override fun onFailure(reason: Int) {
                    emitError(WifiDirectException.DiscoveryException(message = "Discovery failed: Reason code $reason"))
                    updateState { copy(isDiscovering = false) }
                }
            })
        }.onFailure { e ->
            Log.w("WifiDirectController", "Security exception during discoverPeers", e)
            updateState { copy(isDiscovering = false) }
        }
    }

    /**
     * Requests the current list of discovered peers from the Wi-Fi P2P Manager.
     * Called when the system notifies us that the peer list has changed.
     */
    private fun requestPeerList() {
        runCatching {
            manager.requestPeers(channel) { peers ->
                val peerList = peers.deviceList.map { device ->
                    val deviceName = device.deviceName ?: "Unknown Device"
                    val macAddress = device.deviceAddress ?: ""
                    // Port is generally negotiated later, default placeholder used here.
                    Peer(deviceName, macAddress, 8080, Peer.Type.WIFI_DIRECT)
                }
                updateState { copy(discoveredPeers = peerList) }
            }
        }.onFailure { e ->
            Log.w("WifiDirectController", "Failed to request peers", e)
        }
    }

    /**
     * Attempts to connect to a specific peer using their MAC address.
     */
    private fun handleConnect(address: String) {
        val config = WifiP2pConfig().apply { deviceAddress = address }

        runCatching {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i("WifiDirectController", "Connection initiated successfully: $address")
                }

                override fun onFailure(reason: Int) {
                    emitError(WifiDirectException.ConnectionException(message = "Connection failed: Reason code $reason"))
                }
            })
        }.onFailure { e ->
            emitError(WifiDirectException.ConnectionException(message = "System rejected connection request", cause = e))
        }
    }

    /**
     * Fetches the connection info to determine the Group Owner IP after a successful connection.
     */
    private fun requestInfo() {
        runCatching {
            manager.requestConnectionInfo(channel) { info ->
                if (info.groupFormed) {
                    val hostIp = info.groupOwnerAddress?.hostAddress ?: ""
                    updateState { copy(isConnected = true, isGroupOwner = info.isGroupOwner) }
                    emitEffect(WifiDirectEffect.ConnectionEstablished(hostIp))
                }
            }
        }.onFailure { e ->
            Log.w("WifiDirectController", "Failed to request connection info", e)
        }
    }

    /**
     * Creates a P2P Group, making this device the Group Owner (Hub).
     */
    private fun handleCreateGroup() {
        runCatching {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    updateState { copy(isGroupOwner = true) }
                }

                override fun onFailure(reason: Int) {
                    emitError(WifiDirectException.GroupException(message = "Group creation failed: Reason code $reason"))
                }
            })
        }.onFailure { e ->
            emitError(WifiDirectException.GroupException(message = "System rejected group creation", cause = e))
        }
    }

    /**
     * Tears down the P2P Group and disconnects.
     */
    private fun handleRemoveGroup() {
        runCatching {
            manager.removeGroup(channel, null)
        }.onFailure { e ->
            Log.w("WifiDirectController", "Failed to remove group cleanly", e)
        }

        updateState {
            copy(
                isConnected = false,
                isGroupOwner = false,
                discoveredPeers = emptyList()
            )
        }
    }

    /**
     * Halts the discovery process.
     */
    private fun handleStopDiscovery() {
        runCatching {
            manager.stopPeerDiscovery(channel, null)
        }.onFailure { e ->
            Log.w("WifiDirectController", "Failed to stop discovery cleanly", e)
        }
        updateState { copy(isDiscovering = false) }
    }

    /**
     * Registers a BroadcastReceiver to listen for asynchronous Wi-Fi Direct events dispatched by the OS.
     */
    private fun registerWifiDirectReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }

        wifiDirectReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (state.value.isDiscovering) {
                            requestPeerList()
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            requestInfo()
                        } else {
                            updateState { copy(isConnected = false, isGroupOwner = false) }
                        }
                    }
                }
            }
        }
        context.registerReceiver(wifiDirectReceiver, intentFilter)
    }

    /**
     * Prevents memory leaks by ensuring the BroadcastReceiver is unregistered
     * when the controller is destroyed.
     */
    override fun clear() {
        handleStopDiscovery()
        handleRemoveGroup()
        wifiDirectReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        wifiDirectReceiver = null
        super.clear()
    }
}