package io.goodmidnight.transfer.core.connection.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.connection.model.Peer
import io.goodmidnight.transfer.core.base.BaseController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Handles Network Service Discovery (NSD / Bonjour / mDNS) for P2P connections over a shared Wi-Fi network.
 * It broadcasts the device's presence and discovers other devices running the same service.
 * Supports both Legacy (Pre-Android 14) resolving and Modern (Android 14+) continuous monitoring.
 */
class NsdController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<NsdState, NsdEvent, NsdEffect, NsdException>("NsdController", NsdState()) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Executor required for Android 14+ (API 34) ServiceInfoCallback to run on a background thread.
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    // [Refactored] Thread-safe map to prevent ConcurrentModificationException during unregistration.
    private val activeServiceCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

    // Dynamically assigned by the OS upon successful registration to prevent naming collisions.
    private var myServiceName = ""

    override suspend fun handleEvent(event: NsdEvent) {
        when (event) {
            is NsdEvent.StartDiscovery -> handleStartDiscovery()
            is NsdEvent.StopDiscovery -> handleStopDiscovery()
            is NsdEvent.StartBroadcasting -> handleStartBroadcasting(event.deviceName, event.port)
            is NsdEvent.StopBroadcasting -> handleStopBroadcasting()
            is NsdEvent.ConnectToPeer -> handleConnectToPeer(event.ipAddress, event.port)
        }
    }

    /**
     * Begins searching the local network for other devices broadcasting our specific SERVICE_TYPE.
     */
    private fun handleStartDiscovery() {
        updateState { copy(isDiscovering = true, discoveredPeers = emptyList()) }
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { e ->
            Log.w("NsdController", "Failed to start service discovery", e)
        }
    }

    /**
     * Halts the ongoing discovery process and cleans up any active monitoring callbacks.
     */
    private fun handleStopDiscovery() {
        runCatching {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }.onFailure { e ->
            Log.w("NsdController", "Failed to stop discovery cleanly", e)
        }

        // Clean up all active monitoring callbacks for Android 14+ devices to prevent memory leaks.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activeServiceCallbacks.values.forEach { callback ->
                runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
            }
            activeServiceCallbacks.clear()
        }

        updateState { copy(isDiscovering = false) }
    }

    /**
     * Registers this device's service on the local network so other peers can discover it.
     */
    private fun handleStartBroadcasting(deviceName: String, port: Int) {
        updateState { copy(isBroadcasting = true) }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = deviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        runCatching {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        }.onFailure {
            emitError(
                NsdException.BroadcastException(
                    message = "Failed to initiate service registration",
                    errorCode = NsdErrorCode.BROADCAST_START_FAILED
                )
            )
        }
    }

    /**
     * Unregisters the service, effectively hiding this device from the local network discovery.
     */
    private fun handleStopBroadcasting() {
        runCatching {
            nsdManager.unregisterService(registrationListener)
        }.onFailure { e ->
            Log.w("NsdController", "Failed to unregister service cleanly", e)
        }
        updateState { copy(isBroadcasting = false, myDeviceName = "") }
    }
    /**
     * Halts discovery and signals the UI/ConnectionManager to initiate a socket connection.
     */
    private fun handleConnectToPeer(ipAddress: String, port: Int) {
        handleStopDiscovery()
        emitEffect(NsdEffect.ConnectionEstablished(ipAddress, port))
    }

    /**
     * Factory function to create a new ResolveListener (Pre-Android 14).
     * Note: NSD requires a unique listener instance for every single resolve request.
     */
    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w("NsdController", "Resolve failed for ${serviceInfo.serviceName}: Code $errorCode")
        }

        @Suppress("DEPRECATION")
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val ip = serviceInfo.host?.hostAddress ?: return
            addOrUpdatePeer(serviceInfo.serviceName, ip, serviceInfo.port)
        }
    }
    /**
     * Factory function to create a continuous monitoring callback (Android 14+).
     */
    private fun createServiceInfoCallback(serviceName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

                override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                    val ip = updatedInfo.hostAddresses.firstOrNull()?.hostAddress ?: return
                    addOrUpdatePeer(updatedInfo.serviceName, ip, updatedInfo.port)
                }

                override fun onServiceLost() {}

                override fun onServiceInfoCallbackUnregistered() {
                    activeServiceCallbacks.remove(serviceName)
                }
            }
        } else null

    /**
     * Safely adds a newly resolved peer to the state list or updates it if it already exists.
     */
    private fun addOrUpdatePeer(name: String, ip: String, port: Int) {
        val newPeer = Peer(name, ip, port, Peer.Type.NSD)
        updateState {
            copy(discoveredPeers = (discoveredPeers.filterNot { it.deviceName == name } + newPeer))
        }
    }

    /**
     * Listener to monitor the lifecycle of broadcasting this device's service.
     */
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            myServiceName = nsdServiceInfo.serviceName
            updateState { copy(myDeviceName = myServiceName) }
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            handleStopBroadcasting()
            emitError(
                NsdException.BroadcastException(
                    message = "Failed to start broadcasting",
                    errorCode = NsdErrorCode.BROADCAST_START_FAILED
                )
            )
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            updateState { copy(isBroadcasting = false, myDeviceName = "") }
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            handleStopBroadcasting()
        }
    }

    /**
     * Listener to monitor the discovery of other devices on the network.
     */
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceName == myServiceName) return

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
            //  Clean up callback explicitly when service leaves the network
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activeServiceCallbacks.remove(service.serviceName)?.let { callback ->
                    runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
                }
            }
            updateState {
                copy(discoveredPeers = discoveredPeers.filterNot { it.deviceName == service.serviceName })
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            updateState { copy(isDiscovering = false) }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            handleStopDiscovery()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            handleStopDiscovery()
        }
    }

    // Release Executor resources when Controller is destroyed
    override fun clear() {
        handleStopDiscovery()
        handleStopBroadcasting()
        callbackExecutor.shutdownNow()
        super.clear()
    }

    companion object {
        private const val SERVICE_TYPE = "_transfer._tcp."
    }
}