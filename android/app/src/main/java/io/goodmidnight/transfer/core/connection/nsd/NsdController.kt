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

class NsdController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<NsdState, NsdEvent, NsdEffect, NsdException>("NsdController", NsdState()) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val activeServiceCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()
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

    private fun handleStartDiscovery() {
        Log.d(TAG, "Starting discovery: type=$SERVICE_TYPE")
        updateState { copy(isDiscovering = true, discoveredPeers = emptyList()) }
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { e ->
            Log.e(TAG, "Failed to start service discovery", e)
            updateState { copy(isDiscovering = false) }
        }
    }

    private fun handleStopDiscovery() {
        Log.d(TAG, "Stopping discovery")
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activeServiceCallbacks.values.forEach { callback ->
                runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
            }
            activeServiceCallbacks.clear()
        }
        updateState { copy(isDiscovering = false) }
    }

    private fun handleStartBroadcasting(deviceName: String, port: Int) {
        Log.i(TAG, "Starting broadcasting: Name=$deviceName, Port=$port")
        updateState { copy(isBroadcasting = true) }
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = deviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure { e ->
            Log.e(TAG, "Failed to initiate service registration", e)
            emitError(NsdException.BroadcastException(message = "Registration failed", errorCode = NsdErrorCode.BROADCAST_START_FAILED))
            updateState { copy(isBroadcasting = false) }
        }
    }

    private fun handleStopBroadcasting() {
        Log.d(TAG, "Stopping broadcasting")
        runCatching { nsdManager.unregisterService(registrationListener) }
        updateState { copy(isBroadcasting = false, myDeviceName = "") }
    }

    private fun handleConnectToPeer(ipAddress: String, port: Int) {
        Log.d(TAG, "Connecting to peer: $ipAddress:$port")
        handleStopDiscovery()
        emitEffect(NsdEffect.ConnectionEstablished(ipAddress, port))
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: Code $errorCode")
        }
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val ip = serviceInfo.host?.hostAddress ?: return
            Log.i(TAG, "Service resolved: ${serviceInfo.serviceName} at $ip:${serviceInfo.port}")
            addOrUpdatePeer(serviceInfo.serviceName, ip, serviceInfo.port)
        }
    }

    private fun createServiceInfoCallback(serviceName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "ServiceInfoCallback registration failed for $serviceName: $errorCode")
                }
                override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                    val ip = updatedInfo.hostAddresses.firstOrNull()?.hostAddress ?: return
                    Log.i(TAG, "Service updated (API 34+): ${updatedInfo.serviceName} at $ip:${updatedInfo.port}")
                    addOrUpdatePeer(updatedInfo.serviceName, ip, updatedInfo.port)
                }
                override fun onServiceLost() { removePeer(serviceName) }
                override fun onServiceInfoCallbackUnregistered() { activeServiceCallbacks.remove(serviceName) }
            }
        } else null

    private fun addOrUpdatePeer(name: String, ip: String, port: Int) {
        val newPeer = Peer(name, ip, port, Peer.Type.NSD)
        updateState {
            val updatedList = (discoveredPeers.filterNot { it.deviceName == name } + newPeer)
            copy(discoveredPeers = updatedList)
        }
    }

    private fun removePeer(name: String) {
        updateState { copy(discoveredPeers = discoveredPeers.filterNot { it.deviceName == name }) }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(si: NsdServiceInfo) {
            Log.i(TAG, "Service registered: ${si.serviceName}")
            myServiceName = si.serviceName
            updateState { copy(myDeviceName = myServiceName) }
        }
        override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) { handleStopBroadcasting() }
        override fun onServiceUnregistered(si: NsdServiceInfo) { updateState { copy(isBroadcasting = false, myDeviceName = "") } }
        override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) { handleStopBroadcasting() }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) { Log.i(TAG, "Discovery started: $regType") }
        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${service.serviceName}")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activeServiceCallbacks.remove(service.serviceName)?.let { callback ->
                    runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
                }
            }
            removePeer(service.serviceName)
        }
        override fun onDiscoveryStopped(serviceType: String) { updateState { copy(isDiscovering = false) } }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { handleStopDiscovery() }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { handleStopDiscovery() }
    }

    override fun clear() {
        handleStopDiscovery()
        handleStopBroadcasting()
        callbackExecutor.shutdownNow()
        super.clear()
    }

    companion object {
        private const val TAG = "NsdController"
        private const val SERVICE_TYPE = "_transfer._tcp"
    }
}
