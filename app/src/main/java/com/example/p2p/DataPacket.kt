package com.example.p2p

import org.json.JSONObject
import java.util.UUID

/**
 * Enumeration of supported P2P DataPacket types in the Living Mesh protocol.
 */
enum class PacketType {
    HANDSHAKE,
    PING,
    PONG,
    DATA_STORE,
    CHAT_MESSAGE,
    ROUTE_ANNOUNCE,
    DISCOVERY
}

/**
 * Lightweight binary/JSON DataPacket structure for decentralized P2P message exchange.
 *
 * @property id Unique message identifier UUID.
 * @property senderNodeId Public key fingerprint / Node ID of the sender.
 * @property targetNodeId Target Node ID or "BROADCAST" for mesh-wide transmission.
 * @property payload Content or serialized data payload.
 * @property timestamp Epoch timestamp in milliseconds when packet was produced.
 * @property signature Base64 cryptographic signature signed by the sender's private key.
 * @property packetType Type of protocol packet.
 * @property ttl Time-To-Live hop counter to prevent broadcast loops.
 */
data class DataPacket(
    val id: String = UUID.randomUUID().toString(),
    val senderNodeId: String,
    val targetNodeId: String = "BROADCAST",
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = "",
    val packetType: PacketType = PacketType.CHAT_MESSAGE,
    val ttl: Int = 5
) {
    /**
     * Converts packet data fields (excluding signature) into canonical byte array for signing and verification.
     */
    fun toSignableBytes(): ByteArray {
        val canonical = "$id|$senderNodeId|$targetNodeId|$payload|$timestamp|${packetType.name}|$ttl"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    /**
     * Creates a signed copy of this packet using the node's IdentityManager.
     */
    fun signPacket(identityManager: NodeIdentityManager): DataPacket {
        val bytesToSign = toSignableBytes()
        val sig = identityManager.signData(bytesToSign)
        return this.copy(signature = sig)
    }

    /**
     * Verifies the cryptographic signature against a known sender public key hex.
     */
    fun verifySignature(senderPublicKeyHex: String): Boolean {
        if (signature.isBlank()) return false
        val bytesToVerify = toSignableBytes()
        return NodeIdentityManager.verifyDataSignature(
            publicKeyHex = senderPublicKeyHex,
            data = bytesToVerify,
            signatureBase64 = signature
        )
    }

    /**
     * Serializes this DataPacket into a compact JSON string for network wire transmission.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("senderNodeId", senderNodeId)
        json.put("targetNodeId", targetNodeId)
        json.put("payload", payload)
        json.put("timestamp", timestamp)
        json.put("signature", signature)
        json.put("packetType", packetType.name)
        json.put("ttl", ttl)
        return json.toString()
    }

    companion object {
        /**
         * Deserializes a DataPacket from a JSON string payload received over the wire.
         */
        fun fromJson(jsonStr: String): DataPacket? {
            return try {
                val json = JSONObject(jsonStr)
                DataPacket(
                    id = json.getString("id"),
                    senderNodeId = json.getString("senderNodeId"),
                    targetNodeId = json.optString("targetNodeId", "BROADCAST"),
                    payload = json.getString("payload"),
                    timestamp = json.getLong("timestamp"),
                    signature = json.optString("signature", ""),
                    packetType = try {
                        PacketType.valueOf(json.getString("packetType"))
                    } catch (e: Exception) {
                        PacketType.CHAT_MESSAGE
                    },
                    ttl = json.optInt("ttl", 5)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
