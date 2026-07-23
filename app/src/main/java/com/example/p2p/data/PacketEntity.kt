package com.example.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room Database entity representing a stored decentralized P2P data block.
 */
@Entity(tableName = "stored_packets")
data class PacketEntity(
    @PrimaryKey val id: String,
    val senderNodeId: String,
    val targetNodeId: String,
    val payload: String,
    val timestamp: Long,
    val signature: String,
    val packetType: String,
    val ttl: Int,
    val isVerified: Boolean = true,
    val hostedBlockSizeBytes: Int = payload.toByteArray().size
)
