package com.indidevs.android.adbeesh.internal.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsConfig
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import com.indidevs.android.adbeesh.internal.Adbeesh
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Represents a discovered ADB service.
 */
internal data class DiscoveredService(
    val name: String,
    val host: InetAddress?,
    val port: Int,
    val type: ServiceType
)

internal enum class ServiceType(val protocol: String) {
    TLS_PAIRING("adb-tls-pairing"),
    TLS_CONNECT("adb-tls-connect");

    val serviceType: String get() = "_$protocol._tcp"

    fun toMdnsServiceType(): MdnsServiceType = when (this) {
        TLS_PAIRING -> MdnsServiceType.TLS_PAIRING
        TLS_CONNECT -> MdnsServiceType.TLS_CONNECT
    }
}

/**
 * Handles mDNS discovery of ADB services using Kadb-Mdns.
 */
internal class Scanner(private val context: Context) {
    /**
     * Starts discovery for a specific ADB service type and returns a Flow of discovered services.
     */
    fun discover(type: ServiceType): Flow<DiscoveredService> = callbackFlow {
        Log.i(TAG, "Initiating discovery for protocol: ${type.protocol}")

        val wifiManager = context.applicationContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager
        val lock = wifiManager.createMulticastLock("adbeesh_discovery_lock").apply {
            setReferenceCounted(false)
            acquire()
        }

        val config = MdnsConfig(serviceTypes = setOf(type.toMdnsServiceType()))
        val discovery = KadbMdnsAndroid(context.applicationContext, config)

        var lastEmitted = emptySet<MdnsEndpoint>()
        val job = discovery.state
            .map { state ->
                (
                    if (type == ServiceType.TLS_PAIRING) {
                        state.pairDevices
                    } else {
                        state.connectDevices
                    }
                    ).toSet()
            }
            .distinctUntilChanged()
            .onEach { currentSet ->
                val newItems = currentSet - lastEmitted
                lastEmitted = currentSet
                newItems.forEach { endpoint ->
                    trySend(endpoint.toDiscoveredService(type))
                }
            }
            .launchIn(this)

        discovery.start()

        awaitClose {
            Log.i(TAG, "Stopping discovery for protocol: ${type.protocol}")
            job.cancel()
            discovery.stop()
            discovery.close()
            lock.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun MdnsEndpoint.toDiscoveredService(type: ServiceType): DiscoveredService {
        return DiscoveredService(
            name = name,
            host = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                null
            },
            port = port,
            type = type
        )
    }

    companion object {
        private const val TAG = "${Adbeesh.LOG_PREFIX}:Scanner"
    }
}
