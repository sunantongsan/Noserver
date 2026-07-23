package com.example.p2p

/**
 * Model representing a peer node discovered on the P2P local network.
 */
data class DiscoveredPeer(
    val nodeId: String,
    val serviceName: String,
    val ipAddress: String,
    val port: Int,
    val publicKeyHex: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val pingMs: Long = -1L
)
