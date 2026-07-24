package com.example.ui.models

import com.example.p2p.DataPacket
import com.example.p2p.PasskeyIdentityAdapter

/**
 * UI Data Model representing a Twitter/X style decentralized social post broadcast across the P2P Mesh.
 */
data class SocialPostModel(
    val id: String,
    val senderNodeId: String,
    val walletAddress: String,
    val content: String,
    val timestamp: Long,
    val signature: String,
    val isVerified: Boolean = true,
    val avatarColorIndex: Int = 0
) {
    companion object {
        fun fromDataPacket(packet: DataPacket): SocialPostModel {
            val shortNodeId = packet.senderNodeId
            val derivedAddress = PasskeyIdentityAdapter.deriveSmartWalletAddress(shortNodeId)
            val colorIdx = (shortNodeId.hashCode() and 0x7FFFFFFF) % 6
            return SocialPostModel(
                id = packet.id,
                senderNodeId = packet.senderNodeId,
                walletAddress = derivedAddress,
                content = packet.payload,
                timestamp = packet.timestamp,
                signature = packet.signature.ifEmpty { "0x00...unsigned" },
                isVerified = true,
                avatarColorIndex = colorIdx
            )
        }
    }
}
