package com.indidevs.android.adbeesh.internal

import android.content.Context
import android.util.Log
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.exceptions.AdbAuthenticationException
import com.indidevs.android.adbeesh.exceptions.AdbException
import com.indidevs.android.adbeesh.exceptions.AdbNotPairedException
import com.indidevs.android.adbeesh.exceptions.AdbTimeoutException
import com.indidevs.android.adbeesh.internal.connection.AdbClient
import com.indidevs.android.adbeesh.internal.connection.AdbSessionImpl
import com.indidevs.android.adbeesh.internal.discovery.DiscoveredService
import com.indidevs.android.adbeesh.internal.discovery.Scanner
import com.indidevs.android.adbeesh.internal.discovery.ServiceType
import com.indidevs.android.adbeesh.internal.security.CertificateFactory
import com.indidevs.android.adbeesh.internal.security.IdentityStore
import com.indidevs.android.adbeesh.internal.security.KeyPair
import com.indidevs.android.adbeesh.pairing.AdbPairing
import com.indidevs.android.adbeesh.pairing.AdbPairingStatus
import com.indidevs.android.adbeesh.pairing.AdbPairingWorkflow
import com.indidevs.android.adbeesh.runtime.AdbSession
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class AdbPairingImpl(
    context: Context,
    override val id: String,
    private val config: AdbSessionConfig,
    private val externalScope: CoroutineScope? = null
) : AdbPairing {

    companion object {
        private const val TAG = "${Adbeesh.LOG_PREFIX}:Pairing"
    }

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appContext = context.applicationContext
    private val identityStore = IdentityStore(appContext, id)
    private val _status = MutableStateFlow(AdbPairingStatus.IDLE)
    override val status: StateFlow<AdbPairingStatus> = _status.asStateFlow()
    private val scanner = Scanner(appContext)

    private val pairingMutex = Mutex()
    private val workflowMutex = Mutex()

    @Volatile
    private var pairingJob: Job? = null

    @Volatile
    private var discoveryJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) { syncIdentity() }
    }

    private suspend fun syncIdentity(): KeyPair? = pairingMutex.withLock {
        identityStore.load()?.also {
            _status.value = AdbPairingStatus.PAIRED
        }
    }

    override suspend fun ensurePaired(workflow: AdbPairingWorkflow) {
        scope.ensureActive()
        val task = scope.async(Dispatchers.IO) {
            workflowMutex.withLock {
                if (syncIdentity() != null) {
                    Log.d(TAG, "[$id] Identity already exists, skipping workflow")
                    return@async
                }

                pairingJob = coroutineContext[Job]
                try {
                    val identity = getOrCreateKeyPair()
                    val client = AdbClient(identity, config.resolve(appContext).deviceName!!)

                    _status.value = AdbPairingStatus.DISCOVERING
                    workflow.onPairingStarted(id)

                    Log.d(TAG, "[$id] Starting pairing discovery")

                    val serviceState = MutableStateFlow<DiscoveredService?>(null)
                    val discoveryError = CompletableDeferred<Exception>()

                    try {
                        discoveryJob = scope.launch {
                            try {
                                scanner.discover(ServiceType.TLS_PAIRING).collect { service ->
                                    serviceState.value = service
                                    workflow.onPortFound(id, service.port)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "[$id] Discovery failed", e)
                                discoveryError.complete(e)
                            }
                        }

                        val firstServiceDeferred = async { serviceState.filterNotNull().first() }
                        withTimeoutOrNull(config.handshakeTimeoutMs) {
                            select<DiscoveredService> {
                                firstServiceDeferred.onAwait { it }
                                discoveryError.onAwait { throw it }
                            }
                        } ?: run {
                            firstServiceDeferred.cancel()
                            if (discoveryError.isCompleted) {
                                throw discoveryError.await()
                            }
                            Log.e(TAG, "[$id] Pairing discovery timed out: No device found")
                            throw AdbTimeoutException("Pairing timed out: No device found")
                        }

                        val service = checkNotNull(serviceState.value)
                        Log.d(
                            TAG,
                            "[$id] Discovered pairing service at ${service.host?.hostAddress}:${service.port}"
                        )
                        _status.value = AdbPairingStatus.AWAITING_CODE

                        val code = workflow.provideCode(id, service.port)
                            ?: run {
                                Log.w(TAG, "[$id] Pairing cancelled by user")
                                throw AdbException("Pairing cancelled by user")
                            }

                        Log.d(TAG, "[$id] Code received, handshaking...")
                        _status.value = AdbPairingStatus.PAIRING
                        workflow.onPairingHandshakeStarted(id)

                        val activeService = checkNotNull(serviceState.value)
                        val host = activeService.host?.hostAddress ?: "127.0.0.1"
                        val success = withTimeoutOrNull(config.handshakeTimeoutMs) {
                            client.pair(host, activeService.port, code)
                        } ?: run {
                            Log.e(TAG, "[$id] Pairing handshake timed out")
                            throw AdbTimeoutException("Pairing timed out during handshake")
                        }

                        if (success) {
                            pairingMutex.withLock {
                                _status.value = AdbPairingStatus.PAIRED
                            }
                            workflow.onPairingSuccess(id)
                            Log.i(TAG, "[$id] Pairing finished successfully")
                        } else {
                            Log.e(TAG, "[$id] Pairing rejected by device")
                            onPairingError(workflow, "Rejected by device")
                            throw AdbAuthenticationException("Pairing failed")
                        }
                    } finally {
                        discoveryJob?.cancel()
                        discoveryJob = null
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _status.value = AdbPairingStatus.IDLE
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[$id] Pairing failed", e)
                    onPairingError(workflow, e.message ?: "Unknown error")
                    throw e
                } finally {
                    pairingJob = null
                }
            }
        }
        task.await()
    }

    override suspend fun openSession(timeoutMs: Long): AdbSession = withContext(Dispatchers.IO) {
        val identity = syncIdentity() ?: run {
            Log.e(TAG, "[$id] openSession failed: Device not paired")
            throw AdbNotPairedException("Device not paired for ID: $id")
        }
        val client = AdbClient(identity, config.resolve(appContext).deviceName!!)

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                scanner.discover(ServiceType.TLS_CONNECT).mapNotNull { service ->
                    val hostAddress = service.host?.hostAddress ?: return@mapNotNull null
                    Log.d(
                        TAG,
                        "[$id] Connecting to TLS_CONNECT service at $hostAddress:${service.port}"
                    )
                    client.connect(hostAddress, service.port)
                }.first()
            }

            if (result != null) {
                Log.i(TAG, "[$id] Session successfully established")
                AdbSessionImpl(result)
            } else {
                Log.e(TAG, "[$id] Connection timed out")
                throw AdbTimeoutException("Could not connect")
            }
        } catch (e: AdbAuthenticationException) {
            Log.w(
                TAG,
                "[$id] Authentication failure (pairing might be revoked), wiping identity",
                e
            )
            unpair()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$id] openSession failed", e)
            throw e
        }
    }

    override fun unpair() {
        discoveryJob?.cancel()
        pairingJob?.cancel()
        _status.value = AdbPairingStatus.IDLE
        identityStore.delete()
    }

    override fun close() {
        discoveryJob?.cancel()
        pairingJob?.cancel()
        _status.value = AdbPairingStatus.IDLE
    }

    private fun onPairingError(workflow: AdbPairingWorkflow, errorMessage: String) {
        _status.value = AdbPairingStatus.ERROR
        workflow.onPairingError(id, errorMessage)
        identityStore.delete()
    }

    private fun getOrCreateKeyPair(): KeyPair {
        val existing = identityStore.load()
        if (existing != null) return existing
        return CertificateFactory.generateKeyPair(
            config.resolve(appContext).certificateSubject!!
        ).also {
            identityStore.save(it)
        }
    }
}
