package com.example.p2p.data

import com.example.p2p.DataPacket
import com.example.p2p.DiscoveredPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeshRepository(
    private val packetDao: PacketDao,
    private val peerDao: PeerDao
) {

    val storedPackets: Flow<List<DataPacket>> = packetDao.getAllPackets().map { entities ->
        entities.map { e ->
            DataPacket(
                id = e.id,
                senderNodeId = e.senderNodeId,
                targetNodeId = e.targetNodeId,
                payload = e.payload,
                timestamp = e.timestamp,
                signature = e.signature,
                packetType = try {
                    com.example.p2p.PacketType.valueOf(e.packetType)
                } catch (_: Exception) {
                    com.example.p2p.PacketType.CHAT_MESSAGE
                },
                ttl = e.ttl
            )
        }
    }

    val totalHostedBytes: Flow<Long> = packetDao.getTotalHostedBytes().map { it ?: 0L }

    val savedPeers: Flow<List<PeerEntity>> = peerDao.getAllPeers()

    suspend fun savePacket(packet: DataPacket, isVerified: Boolean = true) {
        val entity = PacketEntity(
            id = packet.id,
            senderNodeId = packet.senderNodeId,
            targetNodeId = packet.targetNodeId,
            payload = packet.payload,
            timestamp = packet.timestamp,
            signature = packet.signature,
            packetType = packet.packetType.name,
            ttl = packet.ttl,
            isVerified = isVerified
        )
        packetDao.insertPacket(entity)
    }

    suspend fun deletePacket(id: String) {
        packetDao.deletePacket(id)
    }

    suspend fun clearLedger() {
        packetDao.clearAll()
    }

    suspend fun recordPeer(peer: DiscoveredPeer) {
        val entity = PeerEntity(
            nodeId = peer.nodeId,
            serviceName = peer.serviceName,
            ipAddress = peer.ipAddress,
            port = peer.port,
            publicKeyHex = peer.publicKeyHex,
            lastSeen = peer.lastSeen
        )
        peerDao.insertPeer(entity)
    }
}
