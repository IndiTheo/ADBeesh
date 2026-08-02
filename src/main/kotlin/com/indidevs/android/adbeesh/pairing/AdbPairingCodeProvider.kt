package com.indidevs.android.adbeesh.pairing

/**
 * Interface for providing a 6-digit ADB pairing code.
 * Implementations are responsible for the UI/UX of collecting the code from the user.
 */
interface AdbPairingCodeProvider {
    /**
     * Called when the system is ready to perform the pairing handshake with the discovered device.
     * @param sessionId The unique ID of the pairing session.
     * @param port The port number discovered for pairing.
     * @return The 6-digit code entered by the user, or null if cancelled.
     */
    suspend fun provideCode(sessionId: String, port: Int): String?
}
