package com.example.p2p

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Data class representing a single encrypted, signed chunk of a sharded file or large data payload.
 */
data class DataShard(
    val fileId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkHash: String,
    val chunkDataBase64: String,
    val ivBase64: String,
    val authorNodeId: String,
    val signature: String = ""
) {
    /**
     * Canonical string representation used for cryptographic signature signing and verification.
     */
    fun toSignableBytes(): ByteArray {
        val canonical = "$fileId|$chunkIndex|$totalChunks|$chunkHash|$chunkDataBase64|$ivBase64|$authorNodeId"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("fileId", fileId)
        json.put("chunkIndex", chunkIndex)
        json.put("totalChunks", totalChunks)
        json.put("chunkHash", chunkHash)
        json.put("chunkDataBase64", chunkDataBase64)
        json.put("ivBase64", ivBase64)
        json.put("authorNodeId", authorNodeId)
        json.put("signature", signature)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): DataShard? {
            return try {
                val json = JSONObject(jsonStr)
                DataShard(
                    fileId = json.getString("fileId"),
                    chunkIndex = json.getInt("chunkIndex"),
                    totalChunks = json.getInt("totalChunks"),
                    chunkHash = json.getString("chunkHash"),
                    chunkDataBase64 = json.getString("chunkDataBase64"),
                    ivBase64 = json.getString("ivBase64"),
                    authorNodeId = json.getString("authorNodeId"),
                    signature = json.optString("signature", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Result wrapper for encryption and sharding operation.
 */
data class ShardingResult(
    val fileId: String,
    val shards: List<DataShard>,
    val secretKeyBytes: ByteArray,
    val totalOriginalSizeBytes: Int
)

/**
 * Production-ready Zero-Knowledge Encryption & Data Sharding Module.
 * Encrypts payloads with AES-256-GCM, splits them into signed binary chunks,
 * and reassembles/decrypts them safely with signature and SHA-256 hash validation.
 */
class CryptoSharder(
    private val identityManager: NodeIdentityManager
) {

    private val tag = "CryptoSharder"
    private val defaultChunkSizeBytes = 16384 // 16 KB chunk size for P2P network safe framing

    /**
     * Encrypts raw data bytes with AES-256-GCM and shards into signed DataShards.
     */
    fun encryptAndShard(
        plainBytes: ByteArray,
        customChunkSizeBytes: Int = defaultChunkSizeBytes,
        customSecretKeyBytes: ByteArray? = null
    ): ShardingResult {
        val fileId = UUID.randomUUID().toString()

        // 1. Generate or use 256-bit AES Secret Key
        val secretKey: SecretKey = if (customSecretKeyBytes != null) {
            SecretKeySpec(customSecretKeyBytes, "AES")
        } else {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, SecureRandom())
            keyGen.generateKey()
        }

        // 2. Generate 12-byte IV for GCM Mode
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        // 3. Encrypt payload with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(plainBytes)

        // 4. Split encrypted payload into equal chunks
        val chunkSize = if (customChunkSizeBytes > 0) customChunkSizeBytes else defaultChunkSizeBytes
        val totalChunks = kotlin.math.ceil(encryptedBytes.size.toDouble() / chunkSize).toInt().coerceAtLeast(1)
        val shardsList = mutableListOf<DataShard>()

        for (i in 0 until totalChunks) {
            val startPos = i * chunkSize
            val endPos = minOf(encryptedBytes.size, startPos + chunkSize)
            val chunkBytes = encryptedBytes.copyOfRange(startPos, endPos)

            val chunkDataBase64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
            val chunkHash = computeSha256Hex(chunkBytes)

            val unsignedShard = DataShard(
                fileId = fileId,
                chunkIndex = i,
                totalChunks = totalChunks,
                chunkHash = chunkHash,
                chunkDataBase64 = chunkDataBase64,
                ivBase64 = ivBase64,
                authorNodeId = identityManager.nodeId,
                signature = ""
            )

            // Sign shard with identity manager
            val sig = identityManager.signData(unsignedShard.toSignableBytes())
            val signedShard = unsignedShard.copy(signature = sig)
            shardsList.add(signedShard)
        }

        Log.i(tag, "Successfully encrypted & sharded $fileId: ${plainBytes.size} bytes into $totalChunks shards (${shardsList.size} items)")
        return ShardingResult(
            fileId = fileId,
            shards = shardsList,
            secretKeyBytes = secretKey.encoded,
            totalOriginalSizeBytes = plainBytes.size
        )
    }

    /**
     * Verifies, reassembles, and decrypts a list of DataShards back to original unencrypted byte array.
     */
    fun reassembleAndDecrypt(
        shards: List<DataShard>,
        secretKeyBytes: ByteArray
    ): ByteArray? {
        if (shards.isEmpty()) {
            Log.e(tag, "Reassembly failed: Empty shards list")
            return null
        }

        val firstShard = shards.first()
        val totalChunksExpected = firstShard.totalChunks
        val fileId = firstShard.fileId

        // 1. Verify all expected chunk indices are present
        val shardIndexMap = shards.associateBy { it.chunkIndex }
        if (shardIndexMap.size < totalChunksExpected) {
            Log.e(tag, "Reassembly failed for $fileId: Missing chunks (have ${shardIndexMap.size}/$totalChunksExpected)")
            return null
        }

        // 2. Validate cryptographic signatures and SHA-256 hashes for each chunk
        for (i in 0 until totalChunksExpected) {
            val shard = shardIndexMap[i]
            if (shard == null) {
                Log.e(tag, "Reassembly failed for $fileId: Missing chunk index $i")
                return null
            }

            // Signature verification
            if (shard.signature.isNotBlank()) {
                val isSigValid = NodeIdentityManager.verifyDataSignature(
                    publicKeyHex = shard.authorNodeId,
                    data = shard.toSignableBytes(),
                    signatureBase64 = shard.signature
                )
                if (!isSigValid) {
                    Log.e(tag, "Reassembly failed for $fileId: Corrupted or forged signature on chunk index $i")
                    return null
                }
            }

            // Hash verification
            val chunkBytes = Base64.decode(shard.chunkDataBase64, Base64.NO_WRAP)
            val computedHash = computeSha256Hex(chunkBytes)
            if (computedHash != shard.chunkHash) {
                Log.e(tag, "Reassembly failed for $fileId: SHA-256 hash mismatch on chunk index $i")
                return null
            }
        }

        // 3. Reassemble full encrypted byte array in index order
        val byteArrayBuffer = java.io.ByteArrayOutputStream()
        for (i in 0 until totalChunksExpected) {
            val shard = shardIndexMap[i]!!
            val chunkBytes = Base64.decode(shard.chunkDataBase64, Base64.NO_WRAP)
            byteArrayBuffer.write(chunkBytes)
        }
        val fullEncryptedBytes = byteArrayBuffer.toByteArray()

        // 4. Decrypt full payload with AES-256-GCM
        return try {
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")
            val iv = Base64.decode(firstShard.ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(fullEncryptedBytes)
            Log.i(tag, "Successfully reassembled & decrypted $fileId: ${decryptedBytes.size} bytes")
            decryptedBytes
        } catch (e: Exception) {
            Log.e(tag, "Decryption failed for fileId $fileId: ${e.message}", e)
            null
        }
    }

    private fun computeSha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }
}
