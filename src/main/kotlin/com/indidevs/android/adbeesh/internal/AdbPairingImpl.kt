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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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

                    Log.d(TAG, "[$id] Starting discovery")

                    supervisorScope {
                        val discoveryFlow = scanner.discover(ServiceType.TLS_PAIRING)
                            .shareIn(this, SharingStarted.WhileSubscribed(), replay = 1)

                        val firstService = withTimeoutOrNull(config.handshakeTimeoutMs) {
                            discoveryFlow.first()
                        } ?: throw AdbTimeoutException("Pairing timed out: No device found")

                        var latestService: DiscoveredService = firstService
                        _status.value = AdbPairingStatus.AWAITING_CODE
                        workflow.onPortFound(id, firstService.port)

                        val discoveryJob = launch {
                            discoveryFlow.collect { service ->
                                latestService = service
                                workflow.onPortFound(id, service.port)
                            }
                        }

                        val code = workflow.provideCode(id, latestService.port)
                            ?: throw AdbException("Pairing cancelled by user")

                        discoveryJob.cancel()

                        Log.d(TAG, "[$id] Code received, handshaking...")
                        _status.value = AdbPairingStatus.PAIRING
                        workflow.onPairingHandshakeStarted(id)

                        val host = latestService.host?.hostAddress ?: "127.0.0.1"
                        val success = withTimeoutOrNull(config.handshakeTimeoutMs) {
                            client.pair(host, latestService.port, code)
                        } ?: throw AdbTimeoutException("Pairing timed out during handshake")

                        if (success) {
                            pairingMutex.withLock {
                                _status.value = AdbPairingStatus.PAIRED
                                workflow.onPairingSuccess(id)
                            }
                        } else {
                            _status.value = AdbPairingStatus.ERROR
                            workflow.onPairingError(id, "Rejected by device")
                            throw AdbAuthenticationException("Pairing failed")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _status.value = AdbPairingStatus.IDLE
                    throw e
                } catch (e: Exception) {
                    _status.value = AdbPairingStatus.ERROR
                    workflow.onPairingError(id, e.message ?: "Unknown error")
                    throw e
                } finally {
                    pairingJob = null
                }
            }
        }
        task.await()
    }

    override suspend fun openSession(timeoutMs: Long): AdbSession = withContext(Dispatchers.IO) {
        val identity = syncIdentity() ?: throw AdbNotPairedException(
            "Device not paired for ID: $id"
        )
        val client = AdbClient(identity, config.resolve(appContext).deviceName!!)

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                scanner.discover(ServiceType.TLS_CONNECT).mapNotNull { service ->
                    val hostAddress = service.host?.hostAddress ?: return@mapNotNull null
                    client.connect(hostAddress, service.port)
                }.first()
            }

            result?.let { AdbSessionImpl(it) } ?: throw AdbTimeoutException("Could not connect")
        } catch (e: AdbAuthenticationException) {
            Log.w(TAG, "[$id] Authentication failure (pairing might be revoked), wiping identity")
            unpair()
            throw e
        }
    }

    override fun unpair() {
        pairingJob?.cancel()
        scope.launch(Dispatchers.IO + NonCancellable) {
            pairingMutex.withLock {
                _status.value = AdbPairingStatus.IDLE
                identityStore.delete()
            }
        }
    }

    override fun close() {
        pairingJob?.cancel()
        if (externalScope == null) {
            scope.cancel()
        }
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
