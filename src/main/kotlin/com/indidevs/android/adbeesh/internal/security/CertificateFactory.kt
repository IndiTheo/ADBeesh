package com.indidevs.android.adbeesh.internal.security

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Responsible for generating RSA key pairs and self-signed X.509 certificates.
 */
internal object CertificateFactory {
    private const val DEFAULT_ALGORITHM = "SHA256withRSA"
    private const val KEY_ALGORITHM = "RSA"

    private val bcProvider = BouncyCastleProvider()

    fun generateKeyPair(subject: String, keySize: Int = 2048, validityDays: Long = 365): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(keySize, SecureRandom())

        val keyPair = keyPairGenerator.generateKeyPair()
        val expiryDate = System.currentTimeMillis() + (validityDays * 24 * 60 * 60 * 1000L)

        val certificate = generateRSACert(
            keyPair.private,
            keyPair.public,
            subject,
            expiryDate
        )

        return KeyPair(keyPair.private, certificate)
    }

    private fun generateRSACert(
        privateKey: PrivateKey,
        publicKey: PublicKey,
        formattedSubject: String,
        expiryTimeMillis: Long
    ): X509Certificate {
        val issuer = X500Name(formattedSubject)
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis())

        // Start in the year 2000 to avoid issues if the device clock is reset or rewound.
        val notBefore = Date(946684800000L) // Jan 1, 2000
        val notAfter = Date(expiryTimeMillis)

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            notBefore,
            notAfter,
            issuer,
            publicKey
        )

        val contentSigner = JcaContentSignerBuilder(DEFAULT_ALGORITHM)
            .setProvider(bcProvider)
            .build(privateKey)

        return JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certBuilder.build(contentSigner))
    }
}

internal data class KeyPair(val privateKey: PrivateKey, val certificate: X509Certificate)
