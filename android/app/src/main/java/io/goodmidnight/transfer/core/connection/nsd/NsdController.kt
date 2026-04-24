package io.goodmidnight.transfer.core.connection.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.exception.ApplicationException
import io.goodmidnight.transfer.core.exception.CommonException
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
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Handles Network Service Discovery (NSD / Bonjour / mDNS) for P2P connections over a shared Wi-Fi network.
 * It broadcasts the device's presence and discovers other devices running the same service.
 * Manages its own State, Event, and Effect pipelines adhering to Unidirectional Data Flow (UDF).
 * * Supports both Legacy (Pre-Android 14) resolving and Modern (Android 14+) continuous monitoring.
 */
class NsdController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("NsdController"))
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Executor required for Android 14+ (API 34) ServiceInfoCallback to run on a background thread.
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    // Maintains a map of active callbacks to ensure they can be cleanly unregistered later (Android 14+).
    private val activeServiceCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    // Dynamically assigned by the OS upon successful registration to prevent naming collisions.
    private var myServiceName = ""

    private val _state = MutableStateFlow(NsdState())
    val state: StateFlow<NsdState> = _state.asStateFlow()

    private val _event = Channel<NsdEvent>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    private val _effect = MutableSharedFlow<NsdEffect>()
    val effect = _effect.asSharedFlow()

    init {
        observeEvents()
    }

    // Define the immutable mDNS service type using a companion object to optimize memory allocation.
    companion object {
        private const val SERVICE_TYPE = "_transfer._tcp."
    }

    /** Exposes a thread-safe method to send user intents or system commands to this controller. */
    suspend fun processEvent(event: NsdEvent) {
        _event.send(event)
    }

    /** Observes incoming events sequentially and routes them to the appropriate handler. */
    private fun observeEvents() {
        scope.launch {
            _event.receiveAsFlow().collect { event ->
                when (event) {
                    is NsdEvent.StartDiscovery -> handleStartDiscovery()
                    is NsdEvent.StopDiscovery -> handleStopDiscovery()
                    is NsdEvent.StartBroadcasting -> handleStartBroadcasting(event.deviceName, event.port)
                    is NsdEvent.StopBroadcasting -> handleStopBroadcasting()
                    is NsdEvent.ConnectToPeer -> handleConnectToPeer(event.ipAddress, event.port)
                }
            }
        }
    }

    /**
     * Begins searching the local network for other devices broadcasting our specific SERVICE_TYPE.
     */
    private fun handleStartDiscovery() {
        _state.update { it.copy(isDiscovering = true, discoveredPeers = emptyList()) }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /** Halts the ongoing discovery process and cleans up any active monitoring callbacks. */
    private fun handleStopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)

            // Clean up all active monitoring callbacks for Android 14+ devices to prevent memory leaks.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activeServiceCallbacks.values.forEach { callback ->
                    nsdManager.unregisterServiceInfoCallback(callback)
                }
                activeServiceCallbacks.clear()
            }
        } catch (e: Exception) {
            // Can throw if discovery wasn't active or the listener is already unregistered.
            emitError(CommonException.UnknownException(cause = e))
        }
        _state.update { it.copy(isDiscovering = false) }
    }

    /**
     * Registers this device's service on the local network so other peers can discover it.
     */
    private fun handleStartBroadcasting(deviceName: String, port: Int) {
        _state.update { it.copy(isBroadcasting = true) }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = deviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener
        )
    }

    /**
     * Unregisters the service, effectively hiding this device from the local network discovery.
     */
    private fun handleStopBroadcasting() {
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            emitError(CommonException.UnknownException(cause = e))
        }
        _state.update { it.copy(isBroadcasting = false, myDeviceName = "") }
    }

    /**
     * Halts discovery and signals the ConnectionManager to initiate a socket connection.
     * Since NSD fully resolves the IP and Port during discovery, no additional handshake is needed.
     */
    private fun handleConnectToPeer(ipAddress: String, port: Int) {
        handleStopDiscovery()
        scope.launch {
            _effect.emit(NsdEffect.ConnectionEstablished(ipAddress, port))
        }
    }

    /**
     * Factory function to create a new ResolveListener.
     * Note: NSD requires a unique listener instance for every single resolve request.
     * Reusing a listener will cause the Android OS to crash the application.
     */
    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            emitError(NsdException.DiscoveryException(message = "Resolve failed: Code $errorCode", errorCode = NsdErrorCode.RESOLVE_FAILED))
        }

        @Suppress("DEPRECATION")
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val ip = serviceInfo.host?.hostAddress ?: return
            addOrUpdatePeer(serviceInfo.serviceName, ip, serviceInfo.port)
        }
    }

    /**
     * Factory function to create a continuous monitoring callback for a discovered service.
     * Unlike the legacy resolve method, this keeps track of dynamic IP changes.
     */
    private fun createServiceInfoCallback(serviceName: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

            // Retrieve the first available IP address (handles multi-host scenarios better).
            override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                val ip = updatedInfo.hostAddresses.firstOrNull()?.hostAddress ?: return
                addOrUpdatePeer(updatedInfo.serviceName, ip, updatedInfo.port)
            }

            // Handle service loss gracefully if required by future business logic.
            override fun onServiceLost() {
            }

            // Remove from the active map to prevent memory leaks once unregistered.
            override fun onServiceInfoCallbackUnregistered() {
                activeServiceCallbacks.remove(serviceName)
            }
        }
    } else null

    /**
     * Safely adds a newly resolved peer to the state list or updates it if it already exists,
     * ensuring no duplicate IPs are shown to the user.
     */
    private fun addOrUpdatePeer(name: String, ip: String, port: Int) {
        val newPeer = Peer(name, ip, port, Peer.Type.NSD)
        _state.update { state ->
            if (state.discoveredPeers.none { it.address == newPeer.address }) {
                state.copy(discoveredPeers = state.discoveredPeers + newPeer)
            } else state
        }
    }

    /**
     * Listener to monitor the lifecycle of broadcasting this device's service.
     */
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            // We must save this final name to filter out our own broadcast during discovery.
            myServiceName = nsdServiceInfo.serviceName
            _state.update { it.copy(myDeviceName = myServiceName) }
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            handleStopBroadcasting()
            emitError(NsdException.BroadcastException(message = "Failed to start broadcasting", errorCode = NsdErrorCode.BROADCAST_START_FAILED))
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            _state.update { it.copy(isBroadcasting = false, myDeviceName = "") }
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            handleStopBroadcasting()
            emitError(NsdException.BroadcastException(message = "Failed to stop broadcasting", errorCode = NsdErrorCode.BROADCAST_STOP_FAILED))
        }
    }

    /**
     * Listener to monitor the discovery of other devices on the network.
     */
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceName == myServiceName) return

            // Branch based on OS version for retrieving IP details.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val callback = createServiceInfoCallback(service.serviceName)
                if (callback != null) {
                    activeServiceCallbacks[service.serviceName] = callback
                    nsdManager.registerServiceInfoCallback(service, callbackExecutor, callback)
                }
            } else {
                nsdManager.resolveService(service, createResolveListener())
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            _state.update { state ->
                state.copy(discoveredPeers = state.discoveredPeers.filterNot { it.deviceName == service.serviceName })
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            _state.update { it.copy(isDiscovering = false) }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            handleStopDiscovery()
            emitError(NsdException.DiscoveryException(message = "Start failed", errorCode = NsdErrorCode.DISCOVERY_START_FAILED))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
            emitError(NsdException.DiscoveryException(message = "Stop failed", errorCode = NsdErrorCode.DISCOVERY_STOP_FAILED))
        }
    }

    /**
     *  Helper method to dispatch exceptions securely to the shared effect flow.
     */
    private fun emitError(exception: ApplicationException) {
        scope.launch {
            _effect.emit(NsdEffect.SendError(exception))
        }
    }
}