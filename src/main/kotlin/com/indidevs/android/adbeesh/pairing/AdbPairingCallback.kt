package com.indidevs.android.adbeesh.pairing

/**
 * Interface for receiving updates about the ADB pairing process lifecycle.
 */
interface AdbPairingCallback {
    /**
     * Called when the pairing process starts.
     * @param sessionId The unique ID of the pairing session.
     */
    fun onPairingStarted(sessionId: String) {}

    /**
     * Called whenever a device pairing port is found or updated on the network.
     * @param sessionId The unique ID of the pairing session.
     * @param port The port number found.
     */
    fun onPortFound(sessionId: String, port: Int) {}

    /**
     * Called when the cryptographic handshake with the device starts.
     * @param sessionId The unique ID of the pairing session.
     */
    fun onPairingHandshakeStarted(sessionId: String) {}

    /**
     * Called when the pairing process is successful.
     * @param sessionId The unique ID of the pairing session.
     */
    fun onPairingSuccess(sessionId: String) {}

    /**
     * Called when the pairing process fails.
     * @param sessionId The unique ID of the pairing session.
     * @param error A description of why it failed.
     */
    fun onPairingError(sessionId: String, error: String) {}
}
