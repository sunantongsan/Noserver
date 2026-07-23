package com.example.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room Database entity representing known P2P network peers.
 */
@Entity(tableName = "network_peers")
data class PeerEntity(
    @PrimaryKey val nodeId: String,
    val serviceName: String,
    val ipAddress: String,
    val port: Int,
    val publicKeyHex: String,
    val lastSeen: Long,
    val totalPacketsExchanged: Int = 0
)
