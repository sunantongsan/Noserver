package com.example.p2p

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Data class representing a Shamir Secret Share (x index and y bytes).
 */
data class KeyShare(
    val index: Int, // x-coordinate (1, 2, or 3)
    val shareBytes: ByteArray
) {
    fun toHex(): String = NodeIdentityManager.bytesToHex(shareBytes)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KeyShare
        return index == other.index && shareBytes.contentEquals(other.shareBytes)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + shareBytes.contentHashCode()
        return result
    }
}

/**
 * Threshold Key Manager implementing a 2-of-3 Shamir's Secret Sharing Scheme over GF(256).
 * Manages keyless user onboarding and non-custodial wallet private key recovery.
 */
class ThresholdKeyManager(private val context: Context) {

    private val tag = "ThresholdKeyManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ThresholdKeyPrefs", Context.MODE_PRIVATE)

    /**
     * Splits a 256-bit (32-byte) secret key into 3 Shares (x=1, x=2, x=3).
     * Any 2 shares are mathematically sufficient to reconstruct the original secret.
     */
    fun splitSecret(secretKey: ByteArray): List<KeyShare> {
        require(secretKey.size == 32) { "Secret key must be exactly 32 bytes (256 bits)" }

        val random = SecureRandom()
        val coefficients = ByteArray(32)
        random.nextBytes(coefficients)

        val share1 = ByteArray(32)
        val share2 = ByteArray(32)
        val share3 = ByteArray(32)

        for (i in 0 until 32) {
            val s = secretKey[i].toInt() and 0xFF
            val a = coefficients[i].toInt() and 0xFF

            // f(x) = s + a * x over GF(256)
            share1[i] = (s xor gfMul(a, 1)).toByte() // x = 1
            share2[i] = (s xor gfMul(a, 2)).toByte() // x = 2
            share3[i] = (s xor gfMul(a, 3)).toByte() // x = 3
        }

        return listOf(
            KeyShare(1, share1),
            KeyShare(2, share2),
            KeyShare(3, share3)
        )
    }

    /**
     * Reconstructs the 256-bit secret key from ANY 2 distinct shares using Lagrange interpolation in GF(256).
     */
    fun combineShares(shareA: KeyShare, shareB: KeyShare): ByteArray {
        require(shareA.index != shareB.index) { "Shares must have distinct x-coordinate indices" }
        require(shareA.shareBytes.size == 32 && shareB.shareBytes.size == 32) { "Share byte length must be 32 bytes" }

        val x1 = shareA.index
        val x2 = shareB.index
        val y1 = shareA.shareBytes
        val y2 = shareB.shareBytes

        // Lagrange basis polynomials at x=0
        // L1(0) = x2 / (x1 XOR x2)
        // L2(0) = x1 / (x1 XOR x2)
        val denom = x1 xor x2
        val invDenom = gfInv(denom)
        val l1 = gfMul(x2, invDenom)
        val l2 = gfMul(x1, invDenom)

        val reconstructedSecret = ByteArray(32)
        for (i in 0 until 32) {
            val val1 = gfMul(y1[i].toInt() and 0xFF, l1)
            val val2 = gfMul(y2[i].toInt() and 0xFF, l2)
            reconstructedSecret[i] = (val1 xor val2).toByte()
        }

        return reconstructedSecret
    }

    /**
     * Saves Local Share (Share 1) securely on device storage.
     */
    fun saveDeviceShare(share: KeyShare) {
        prefs.edit().putString("device_share_1_hex", share.toHex()).apply()
        Log.d(tag, "Device Share 1 saved securely")
    }

    /**
     * Retrieves Local Device Share (Share 1).
     */
    fun getDeviceShare(): KeyShare? {
        val hex = prefs.getString("device_share_1_hex", null) ?: return null
        return try {
            KeyShare(1, NodeIdentityManager.hexToBytes(hex))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypts Passkey/Identity Share (Share 2) using key derived from user email and passkey signature seed.
     */
    fun encryptPasskeyShare(share: KeyShare, userEmail: String, passkeySeed: ByteArray): String {
        val derivedKey = deriveKeyFromPasskey(userEmail, passkeySeed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(share.shareBytes)

        val payload = JSONObject().apply {
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
            put("email", userEmail)
        }.toString()

        prefs.edit().putString("passkey_share_2_encrypted", payload).apply()
        return payload
    }

    /**
     * Decrypts Passkey/Identity Share (Share 2).
     */
    fun decryptPasskeyShare(userEmail: String, passkeySeed: ByteArray): KeyShare? {
        val payloadStr = prefs.getString("passkey_share_2_encrypted", null) ?: return null
        return try {
            val json = JSONObject(payloadStr)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)

            val derivedKey = deriveKeyFromPasskey(userEmail, passkeySeed)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, spec)

            val decryptedBytes = cipher.doFinal(data)
            KeyShare(2, decryptedBytes)
        } catch (e: Exception) {
            Log.e(tag, "Failed to decrypt Passkey Share 2: ${e.message}")
            null
        }
    }

    /**
     * Prepares Mesh Recovery Share (Share 3) encrypted payload for backup onto P2P network.
     */
    fun prepareMeshRecoveryShare(share: KeyShare, userEmail: String): String {
        val salt = MessageDigest.getInstance("SHA-256").digest("mesh_recovery:$userEmail".toByteArray())
        val pbkdf2Spec = PBEKeySpec(userEmail.toCharArray(), salt, 1000, 256)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(pbkdf2Spec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(share.shareBytes)

        return JSONObject().apply {
            put("x", 3)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(encryptedData, Base64.NO_WRAP))
            put("email_hash", NodeIdentityManager.bytesToHex(salt))
        }.toString()
    }

    /**
     * Decrypts Mesh Recovery Share (Share 3) from P2P network recovery payload.
     */
    fun decryptMeshRecoveryShare(recoveryPayloadJson: String, userEmail: String): KeyShare? {
        return try {
            val json = JSONObject(recoveryPayloadJson)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)

            val salt = MessageDigest.getInstance("SHA-256").digest("mesh_recovery:$userEmail".toByteArray())
            val pbkdf2Spec = PBEKeySpec(userEmail.toCharArray(), salt, 1000, 256)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = secretKeyFactory.generateSecret(pbkdf2Spec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(data)
            KeyShare(3, decryptedBytes)
        } catch (e: Exception) {
            Log.e(tag, "Failed to decrypt Mesh Recovery Share 3: ${e.message}")
            null
        }
    }

    private fun deriveKeyFromPasskey(email: String, passkeySeed: ByteArray): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(email.toByteArray(Charsets.UTF_8))
        md.update(passkeySeed)
        val derivedKeyBytes = md.digest()
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    companion object {
        /**
         * Finite Field GF(256) Multiplication with irreducible polynomial x^8 + x^4 + x^3 + x + 1 (0x11B).
         */
        fun gfMul(a: Int, b: Int): Int {
            var p = 0
            var aVar = a and 0xFF
            var bVar = b and 0xFF
            for (i in 0 until 8) {
                if ((bVar and 1) != 0) {
                    p = p xor aVar
                }
                val hiBitSet = (aVar and 0x80) != 0
                aVar = (aVar shl 1) and 0xFF
                if (hiBitSet) {
                    aVar = aVar xor 0x1B
                }
                bVar = bVar ushr 1
            }
            return p and 0xFF
        }

        /**
         * Multiplicative inverse in GF(256) using Fermat's Little Theorem (a^254).
         */
        fun gfInv(a: Int): Int {
            if (a == 0) return 0
            var res = 1
            var base = a and 0xFF
            var exp = 254
            while (exp > 0) {
                if (exp % 2 == 1) {
                    res = gfMul(res, base)
                }
                base = gfMul(base, base)
                exp /= 2
            }
            return res
        }
    }
}
