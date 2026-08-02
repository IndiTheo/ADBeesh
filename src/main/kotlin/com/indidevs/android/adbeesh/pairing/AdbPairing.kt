package com.indidevs.android.adbeesh.pairing

import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.exceptions.AdbAuthenticationException
import com.indidevs.android.adbeesh.exceptions.AdbException
import com.indidevs.android.adbeesh.exceptions.AdbNotPairedException
import com.indidevs.android.adbeesh.exceptions.AdbTimeoutException
import com.indidevs.android.adbeesh.internal.AdbPairingImpl
import com.indidevs.android.adbeesh.runtime.AdbSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Handle for managing the pairing identity and connection lifecycle for a specific ID.
 */
interface AdbPairing : AutoCloseable {
    /**
     * Internal storage identifier used to persist and retrieve the RSA keys for this pairing.
     */
    val id: String

    /**
     * Current status of the pairing process.
     *
     * This flow is initialized based on the state of the encrypted storage for the given [id].
     * If keys already exist on disk, the initial value will be [AdbPairingStatus.PAIRED].
     */
    val status: StateFlow<AdbPairingStatus>

    /**
     * Ensures that the device is paired.
     * If not already paired, it starts the pairing workflow using the provided [AdbPairingWorkflow].
     * @throws [AdbException] if pairing fails.
     */
    suspend fun ensurePaired(workflow: AdbPairingWorkflow)

    /**
     * Opens a new ADB session.
     * This method will attempt to find the device on the network and establish a TLS connection.
     *
     * If the connection is rejected (e.g., because the pairing was revoked in the system settings),
     * this method will throw an [AdbAuthenticationException], automatically call [unpair]
     * to clean up the invalid keys, and transition the [status] to [AdbPairingStatus.IDLE].
     *
     * @param timeoutMs The maximum time to wait for the device to appear and connect.
     * @return A live [AdbSession] for executing commands.
     * @throws [AdbNotPairedException] if the device is not paired.
     * @throws [AdbAuthenticationException] if the credentials are rejected by the device.
     * @throws [AdbTimeoutException] if the connection times out.
     */
    suspend fun openSession(timeoutMs: Long = 10000): AdbSession

    /**
     * Deletes the pairing identity (keys) for this ID.
     */
    fun unpair()

    companion object {
        /**
         * Gets or creates an [AdbPairing] handle for the given ID.
         *
         * The returned instance will automatically attempt to recover any existing pairing
         * identity from the internal encrypted storage. You can check [status] immediately
         * after calling this to see if the identity is already [AdbPairingStatus.PAIRED].
         *
         * Call [close] to release resources and stop any active background work.
         *
         * @param context Application context.
         * @param id A unique internal identifier for the pairing (e.g., "main_service").
         * Use different IDs to maintain multiple independent sets of RSA keys.
         * @param config Optional configuration for the pairing.
         * @param scope The scope that governs the lifetime of the pairing handle.
         * Defaults to the process lifecycle scope. The provided scope must stay alive
         * while pairing workflow is in progress.
         */
        fun get(
            context: android.content.Context,
            id: String = "",
            config: AdbSessionConfig = AdbSessionConfig(),
            scope: CoroutineScope? = null
        ): AdbPairing {
            return AdbPairingImpl(context, id, config, scope)
        }
    }
}
