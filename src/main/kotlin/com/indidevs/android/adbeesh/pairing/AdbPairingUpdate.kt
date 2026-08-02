package com.indidevs.android.adbeesh.pairing

/**
 * Data container for updates on the pairing process.
 *
 * @property sessionId The unique ID of the pairing session.
 * @property port The discovered ADB pairing port (available in [PairingStage.AWAITING_CODE] stage).
 * @property error The error message (available in [PairingStage.ERROR] stage).
 */
data class AdbPairingUpdate(
    /** The unique ID of the pairing session. */
    val sessionId: String,
    /** The discovered ADB pairing port (available in [PairingStage.AWAITING_CODE] stage). */
    val port: Int? = null,
    /** The error message (available in [PairingStage.ERROR] stage). */
    val error: String? = null
)
