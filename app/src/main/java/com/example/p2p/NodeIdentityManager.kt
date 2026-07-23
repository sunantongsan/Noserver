package com.example.p2p

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Manages local node identity and cryptographic keypair using Android KeyStore.
 * The generated KeyPair acts as the Node's P2P Identity and Wallet Address.
 */
class NodeIdentityManager(private val context: Context) {

    private val keyAlias = "LivingMeshNodeKeyAlias"
    private val keyStoreType = "AndroidKeyStore"
    private val signatureAlgorithm = "SHA256withECDSA"

    val nodeId: String
    val publicKeyHex: String

    init {
        val keyPair = getOrCreateKeyPair()
        publicKeyHex = bytesToHex(keyPair.public.encoded)
        nodeId = generateNodeIdFingerprint(keyPair.public.encoded)
    }

    /**
     * Retrieves existing ECDSA KeyPair from KeyStore or generates a new one securely.
     */
    private fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }

        if (keyStore.containsAlias(keyAlias)) {
            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
            val certificate = keyStore.getCertificate(keyAlias)
            if (privateKey != null && certificate != null) {
                return KeyPair(certificate.publicKey, privateKey)
            }
        }

        // Generate new EC KeyPair in Android KeyStore
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            keyStoreType
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).run {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            build()
        }

        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }

    /**
     * Cryptographically signs a byte array payload using the private key.
     * Returns Base64 encoded signature string.
     */
    fun signData(data: ByteArray): String {
        return try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
            val sig = Signature.getInstance(signatureAlgorithm)
            sig.initSign(privateKey)
            sig.update(data)
            val signatureBytes = sig.sign()
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    companion object {
        private const val STATIC_SIG_ALG = "SHA256withECDSA"

        /**
         * Verifies a signature given a public key hex, raw data byte array, and signature string.
         */
        fun verifyDataSignature(
            publicKeyHex: String,
            data: ByteArray,
            signatureBase64: String
        ): Boolean {
            return try {
                val pubKeyBytes = hexToBytes(publicKeyHex)
                val keySpec = X509EncodedKeySpec(pubKeyBytes)
                val keyFactory = KeyFactory.getInstance("EC")
                val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

                val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
                val sig = Signature.getInstance(STATIC_SIG_ALG)
                sig.initVerify(publicKey)
                sig.update(data)
                sig.verify(signatureBytes)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Generates a short SHA-256 node fingerprint from public key encoded bytes.
         */
        fun generateNodeIdFingerprint(publicKeyBytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(publicKeyBytes)
            val fullHex = bytesToHex(digest)
            // Short 16-char node fingerprint (e.g. "NODE-A1B2C3D4E5F67890")
            return "NODE-" + fullHex.take(12).uppercase()
        }

        fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        fun hexToBytes(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                val index = i * 2
                val j = hex.substring(index, index + 2).toInt(16)
                result[i] = j.toByte()
            }
            return result
        }
    }
}
