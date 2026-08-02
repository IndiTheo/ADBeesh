package com.indidevs.android.adbeesh.internal.security

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.indidevs.android.adbeesh.internal.Adbeesh
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Handles persistence of the ADB private key and certificate using EncryptedSharedPreferences.
 */
internal class IdentityStore(private val context: Context, private val id: String) {
    companion object {
        private const val TAG = "${Adbeesh.LOG_PREFIX}:IdentityStore"
        private const val KEY_PRIVATE_KEY = "priv_key"
        private const val KEY_CERTIFICATE = "cert"
        private const val KEY_ALGORITHM = "RSA"
        private const val CERT_TYPE = "X.509"
    }

    private val fileName = "adbeesh_identity_$id"
    private var cachedPrefs: SharedPreferences? = null
    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    private fun getOrInitializePrefs(): SharedPreferences {
        cachedPrefs?.let { return it }

        val hasStrongBox = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(hasStrongBox)
            .build()

        return try {
            createEncryptedPrefs(masterKey)
        } catch (e: Exception) {
            Log.e(TAG, "[$id] Identity store corrupted, performing factory reset", e)
            clearPersistentStorage()
            createEncryptedPrefs(masterKey)
        }.also { cachedPrefs = it }
    }

    private fun createEncryptedPrefs(masterKey: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Synchronized
    fun load(): KeyPair? {
        cachedKeyPair?.let { return it }
        return try {
            val prefs = getOrInitializePrefs()
            val privBase64 = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
            val certBase64 = prefs.getString(KEY_CERTIFICATE, null) ?: return null

            val privBytes = Base64.decode(privBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))

            val certBytes = Base64.decode(certBase64, Base64.DEFAULT)
            val cf = CertificateFactory.getInstance(CERT_TYPE)
            val certificate = cf.generateCertificate(certBytes.inputStream()) as X509Certificate

            KeyPair(privateKey, certificate).also { cachedKeyPair = it }
        } catch (e: Exception) {
            Log.e(TAG, "[$id] Failed to load identity", e)
            null
        }
    }

    @Synchronized
    fun save(keyPair: KeyPair) {
        try {
            getOrInitializePrefs().edit()
                .putString(
                    KEY_PRIVATE_KEY,
                    Base64.encodeToString(keyPair.privateKey.encoded, Base64.DEFAULT)
                )
                .putString(
                    KEY_CERTIFICATE,
                    Base64.encodeToString(keyPair.certificate.encoded, Base64.DEFAULT)
                )
                .apply()
            cachedKeyPair = keyPair
        } catch (e: Exception) {
            Log.e(TAG, "[$id] Failed to save identity", e)
        }
    }

    @Synchronized
    fun delete() {
        cachedKeyPair = null
        val prefs = cachedPrefs
        cachedPrefs = null

        try {
            prefs?.edit()?.clear()?.commit()
        } catch (e: Exception) {
            Log.e(TAG, "[$id] Failed to clear cached prefs", e)
        }

        clearPersistentStorage()
    }

    private fun clearPersistentStorage() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(fileName)
            } else {
                context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$id] Critical failure during identity store wipe", e)
        }
    }
}
