package com.indidevs.android.adbeesh.pairing

/**
 * Represents the current state of a pairing process.
 */
enum class AdbPairingStatus {
    /** No pairing process is active. */
    IDLE,

    /** Searching for ADB services on the network. */
    DISCOVERING,

    /** A pairing service was found, waiting for a pairing code. */
    AWAITING_CODE,

    /** Currently performing the cryptographic handshake with the device. */
    PAIRING,

    /** Successfully paired with the device. */
    PAIRED,

    /** An error occurred during the pairing process. */
    ERROR
}
