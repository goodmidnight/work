package io.goodmidnight.transfer.core.connection.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.core.base.BaseController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller responsible for managing network routing and process binding.
 * Solves the Android "Cellular Data Override" issue: When Android connects to a Wi-Fi
 * network without internet access, it automatically routes all socket traffic through LTE/5G.
 * This controller forces the app's process to strictly use the P2P Wi-Fi interface.
 */
@Singleton
class ConnectivityController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BaseController<ConnectivityState, ConnectivityEvent, ConnectivityEffect, ConnectivityException>(
    "ConnectivityController", ConnectivityState()
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Holds the active network callback to ensure it can be cleanly unregistered later.
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override suspend fun handleEvent(event: ConnectivityEvent) {
        when (event) {
            is ConnectivityEvent.ConnectToHotspot -> handleConnectToHotspot(event.ssid, event.pw)
            is ConnectivityEvent.RequestBinding -> handleRequestBinding()
            is ConnectivityEvent.ReleaseBinding -> handleReleaseBinding()
        }
    }

    /**
     * Requests connection to a specific hotspot using the provided SSID and Password.
     * A system dialog will prompt the user, and upon acceptance, the device will
     * connect and bind the process to this network automatically.
     */
    private fun handleConnectToHotspot(ssid: String, pw: String) {
        if (networkCallback != null) handleReleaseBinding()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(pw)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            setupNetworkCallback(request, isAutoConnect = true)
        } else {
            // For devices below API 29, automatic connection via WifiNetworkSpecifier is not supported.
            // A fallback using WifiManager (deprecated) could be implemented here if needed.
            emitError(ConnectivityException.UnsupportedVersionException())
        }
    }

    /**
     * Simply binds the application process to the currently connected P2P Wi-Fi network.
     */
    private fun handleRequestBinding() {
        if (networkCallback != null) return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        setupNetworkCallback(request, isAutoConnect = false)
    }

    /**
     * Registers the network callback and handles the lifecycle of the requested network.
     */
    private fun setupNetworkCallback(request: NetworkRequest, isAutoConnect: Boolean) {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val linkProperties = connectivityManager.getLinkProperties(network)
                val ipAddress = linkProperties?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<java.net.Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress

                // Force all socket connections from this app to route through the specified network
                val isBound = connectivityManager.bindProcessToNetwork(network)

                if (isBound) {
                    updateState {
                        copy(
                            isWifiAvailable = true,
                            isBound = true,
                            boundNetworkId = network.toString(),
                            localIp = ipAddress
                        )
                    }
                    if (isAutoConnect) {
                        emitEffect(ConnectivityEffect.HotspotConnectedAndBound(network.toString(), ipAddress))
                    } else {
                        emitEffect(ConnectivityEffect.BindingEstablished(network.toString(), ipAddress))
                    }
                } else {
                    emitError(ConnectivityException.BindingFailedException())
                }
            }

            override fun onLost(network: Network) {
                updateState {
                    copy(
                        isWifiAvailable = false,
                        isBound = false,
                        boundNetworkId = null,
                        localIp = null
                    )
                }
                emitEffect(ConnectivityEffect.NetworkLost(network.toString()))
            }

            override fun onUnavailable() {
                emitError(ConnectivityException.RequestNetworkException("User denied the connection or the network was not found."))
            }
        }

        runCatching {
            connectivityManager.requestNetwork(request, networkCallback!!)
        }.onFailure { e ->
            emitError(ConnectivityException.RequestNetworkException(cause = e))
        }
    }

    /**
     * Unregisters the callback and releases the process binding to restore default network routing.
     */
    private fun handleReleaseBinding() {
        networkCallback?.let { callback ->
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }.onFailure { e ->
                Log.w("ConnectivityController", "Failed to unregister network callback: $e")
            }
        }
        networkCallback = null

        // Passing null restores the default routing (e.g., using Cellular data if Wi-Fi has no internet)
        connectivityManager.bindProcessToNetwork(null)

        updateState { copy(isWifiAvailable = false, isBound = false, boundNetworkId = null) }
    }
}
