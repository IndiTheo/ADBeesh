package com.indidevs.android.adbeesh.exceptions

/**
 * Base class for all ADBeesh related exceptions.
 */
open class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when a device cannot be found on the network within the specified timeout.
 */
class AdbTimeoutException(message: String) : AdbException(message)

/**
 * Thrown when authentication fails (e.g., pairing revoked or incorrect code).
 */
class AdbAuthenticationException(
    message: String,
    cause: Throwable? = null
) : AdbException(message, cause)

/**
 * Thrown when the device is not paired and a session is requested.
 */
class AdbNotPairedException(message: String) : AdbException(message)
