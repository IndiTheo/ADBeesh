package com.indidevs.android.adbeesh.pairing

/**
 * Orchestrates the ADB pairing workflow by combining a [AdbPairingCodeProvider]
 * with optional [AdbPairingCallback]s for lifecycle management.
 */
class AdbPairingWorkflow private constructor(
    internal val provider: AdbPairingCodeProvider,
    private val callbacks: List<AdbPairingCallback>
) {

    fun onPairingStarted(sessionId: String) {
        callbacks.forEach { it.onPairingStarted(sessionId) }
    }

    fun onPortFound(sessionId: String, port: Int) {
        callbacks.forEach { it.onPortFound(sessionId, port) }
    }

    fun onPairingHandshakeStarted(sessionId: String) {
        callbacks.forEach { it.onPairingHandshakeStarted(sessionId) }
    }

    fun onPairingSuccess(sessionId: String) {
        callbacks.forEach { it.onPairingSuccess(sessionId) }
    }

    fun onPairingError(sessionId: String, error: String) {
        callbacks.forEach { it.onPairingError(sessionId, error) }
    }

    suspend fun provideCode(sessionId: String, port: Int): String? {
        return provider.provideCode(sessionId, port)
    }

    /**
     * Builder for [AdbPairingWorkflow].
     */
    class Builder {
        private var provider: AdbPairingCodeProvider? = null
        private val callbacks = mutableListOf<AdbPairingCallback>()

        /**
         * Sets the [AdbPairingCodeProvider] to be used for requesting the pairing code.
         */
        fun withCodeProvider(provider: AdbPairingCodeProvider): Builder {
            this.provider = provider
            return this
        }

        /**
         * Adds an [AdbPairingCallback] to receive lifecycle events.
         */
        fun addCallback(callback: AdbPairingCallback): Builder {
            callbacks.add(callback)
            return this
        }

        /**
         * Builds the [AdbPairingWorkflow].
         * @throws [IllegalStateException] if no code provider was set.
         */
        fun build(): AdbPairingWorkflow {
            val p = provider ?: throw IllegalStateException("AdbPairingCodeProvider must be set")
            return AdbPairingWorkflow(p, callbacks.toList())
        }
    }
}
