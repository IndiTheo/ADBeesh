package com.indidevs.android.adbeesh.internal.connection

import android.util.Base64
import android.util.Log
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.exception.AdbPairAuthException
import com.indidevs.android.adbeesh.exceptions.AdbAuthenticationException
import com.indidevs.android.adbeesh.internal.Adbeesh
import com.indidevs.android.adbeesh.internal.security.KeyPair
import java.io.IOException
import java.security.Key
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level client for ADB pairing and connection using Kadb.
 */
internal class AdbClient(
    private val keyPair: KeyPair,
    private val deviceName: String
) {
    companion object {
        private const val TAG = "${Adbeesh.LOG_PREFIX}:Connection"
        private const val PEM_PRIVATE_KEY_TYPE = "PRIVATE KEY"
        private const val PEM_CERTIFICATE_TYPE = "CERTIFICATE"
    }

    private fun ensureIdentity() {
        try {
            val privPem = keyPair.privateKey.toPem(PEM_PRIVATE_KEY_TYPE)
            val certPem = keyPair.certificate.toPem(PEM_CERTIFICATE_TYPE)
            KadbCert.clear()
            KadbCert.importIdentity(privPem.toByteArray(), certPem.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import certificate/key PEM", e)
        }
    }

    suspend fun pair(host: String, port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureIdentity()
            Kadb.pair(host, port, code, deviceName)
            true
        } catch (e: AdbPairAuthException) {
            Log.e(TAG, "[$deviceName] Pairing rejected by device (auth failure)")
            false
        } catch (e: SSLException) {
            Log.e(TAG, "[$deviceName] Pairing failed (TLS error)", e)
            false
        } catch (e: IOException) {
            Log.e(TAG, "[$deviceName] Pairing failed (Network error)", e)
            false
        }
    }

    suspend fun connect(host: String, port: Int): Kadb? = withContext(Dispatchers.IO) {
        val kadb = Kadb.create(host, port)
        try {
            ensureIdentity()
            // Trigger connection handshake
            kadb.supportsFeature("shell_v2")
            kadb
        } catch (e: Exception) {
            kadb.close()
            when (e) {
                is AdbPairAuthException, is SSLException -> {
                    throw AdbAuthenticationException(
                        "ADB authentication failed during connection handshake. " +
                            "The identity may be stale or pairing might be revoked",
                        e
                    )
                }
                is IOException -> null
                else -> throw e
            }
        }
    }

    private fun Any.toPem(type: String): String {
        val encoded = when (this) {
            is Key -> encoded
            is X509Certificate -> encoded
            else -> null
        } ?: throw IllegalArgumentException("Encoded data is null for $type")

        val base64 = Base64.encodeToString(encoded, Base64.DEFAULT)
        return "-----BEGIN $type-----\n$base64-----END $type-----\n"
    }
}
