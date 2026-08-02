package com.indidevs.android.adbeesh.config

import android.content.Context

/**
 * Configuration for the AdbPairing process and session lifecycle.
 *
 * @property identityName The core cryptographic identity name. It determines the Common Name (CN) in the TLS certificate. It also serves as the default [deviceName].
 * @property deviceName The name identifying session in the Android "Paired devices" list. Defaults to [identityName], falling back to the Application name.
 * @property certificateSubject Custom X.500 subject for the TLS certificate (e.g., "CN=MyDevice").
 * @property handshakeTimeoutMs Timeout for the cryptographic handshake in milliseconds.
 */
data class AdbSessionConfig(
    val identityName: String? = "ADBeeshUser",
    val deviceName: String? = null,
    val certificateSubject: String? = null,
    val handshakeTimeoutMs: Long = 30_000L
) {
    /**
     * Resolves dynamic defaults for the configuration.
     */
    internal fun resolve(context: Context): AdbSessionConfig {
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val packageName = context.packageName

        val finalSubject = when {
            certificateSubject != null -> certificateSubject
            else -> {
                val cn = identityName ?: appLabel
                "CN=$cn, O=ADBeesh, OU=$packageName"
            }
        }

        return copy(
            certificateSubject = finalSubject,
            deviceName = deviceName ?: identityName ?: appLabel
        )
    }

    /**
     * Fluent Builder for [AdbSessionConfig].
     */
    class Builder {
        private val default = AdbSessionConfig()

        /** The core cryptographic identity name. */
        var identityName: String? = default.identityName

        /** Sets the session identity name and returns the builder for chaining. */
        fun identityName(value: String) = apply { identityName = value }

        /** The name identifying session in the Android "Paired devices" list. */
        var deviceName: String? = default.deviceName

        /** Sets the device name shown in Android Settings and returns the builder for chaining. */
        fun deviceName(value: String) = apply { deviceName = value }

        /** Custom X.500 subject for the TLS certificate. */
        var certificateSubject: String? = default.certificateSubject

        /** Sets the TLS certificate subject and returns the builder for chaining. */
        fun certificateSubject(value: String) = apply { certificateSubject = value }

        /** Timeout for the cryptographic handshake in milliseconds. */
        var handshakeTimeoutMs: Long = default.handshakeTimeoutMs

        /** Sets the timeout for the pairing handshake and returns the builder for chaining. */
        fun handshakeTimeoutMs(value: Long) = apply { handshakeTimeoutMs = value }

        /** Builds the [AdbSessionConfig] instance. */
        fun build() = AdbSessionConfig(
            identityName = identityName,
            deviceName = deviceName,
            certificateSubject = certificateSubject,
            handshakeTimeoutMs = handshakeTimeoutMs
        )
    }
}
