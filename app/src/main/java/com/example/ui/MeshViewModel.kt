package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.p2p.DataPacket
import com.example.p2p.DiscoveredPeer
import com.example.p2p.NetworkState
import com.example.p2p.NodeIdentityManager
import com.example.p2p.P2pNetworkManager
import com.example.p2p.P2pService
import com.example.p2p.P2pState
import com.example.p2p.PacketType
import com.example.p2p.data.MeshDatabase
import com.example.p2p.data.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeshViewModel(
    private val context: Context
) : ViewModel() {

    private val identityManager = NodeIdentityManager(context.applicationContext)
    val networkManager = P2pNetworkManager(context.applicationContext, identityManager)
    private val repository: MeshRepository

    val nodeId: String = identityManager.nodeId
    val publicKeyHex: String = identityManager.publicKeyHex

    val networkState: StateFlow<NetworkState> = networkManager.networkState
    val p2pState: StateFlow<P2pState> = networkManager.p2pState
    val localReflexiveAddress: StateFlow<String> = networkManager.localReflexiveAddress
    val activePeers: StateFlow<List<DiscoveredPeer>> = networkManager.activePeers
    val isPowerSavingMode: StateFlow<Boolean> = networkManager.isPowerSavingMode

    private val _recentPackets = MutableStateFlow<List<DataPacket>>(emptyList())
    val recentPackets: StateFlow<List<DataPacket>> = _recentPackets.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _batteryLevel = MutableStateFlow(95)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    val storedPackets: StateFlow<List<DataPacket>>
    val totalHostedBytes: StateFlow<Long>

    init {
        val db = MeshDatabase.getInstance(context.applicationContext)
        repository = MeshRepository(db.packetDao(), db.peerDao())

        storedPackets = repository.storedPackets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        totalHostedBytes = repository.totalHostedBytes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

        // Start P2P network manager
        networkManager.startNetwork()

        // Listen for incoming packets
        viewModelScope.launch {
            networkManager.incomingPackets.collect { packet ->
                // Add to recent packet list (up to 50 items)
                val current = _recentPackets.value.toMutableList()
                current.add(0, packet)
                if (current.size > 50) current.removeAt(current.size - 1)
                _recentPackets.value = current

                // Automatically store DATA_STORE packets in local Room database
                if (packet.packetType == PacketType.DATA_STORE) {
                    val isSigValid = packet.verifySignature(packet.senderNodeId)
                    repository.savePacket(packet, isVerified = isSigValid)
                }
            }
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun broadcastMessage(payload: String) {
        if (payload.isBlank()) return
        val packet = DataPacket(
            senderNodeId = nodeId,
            targetNodeId = "BROADCAST",
            payload = payload,
            packetType = PacketType.CHAT_MESSAGE
        ).signPacket(identityManager)

        // Save locally in recent stream
        val current = _recentPackets.value.toMutableList()
        current.add(0, packet)
        _recentPackets.value = current

        networkManager.broadcastPacket(payload, PacketType.CHAT_MESSAGE)
    }

    fun sendDirectMessage(peerIp: String, peerPort: Int, payload: String, targetNodeId: String) {
        if (payload.isBlank()) return
        val packet = DataPacket(
            senderNodeId = nodeId,
            targetNodeId = targetNodeId,
            payload = payload,
            packetType = PacketType.CHAT_MESSAGE
        ).signPacket(identityManager)

        val current = _recentPackets.value.toMutableList()
        current.add(0, packet)
        _recentPackets.value = current

        networkManager.sendDirectPacket(peerIp, peerPort, payload, targetNodeId)
    }

    fun sendPing(peerIp: String, peerPort: Int, targetNodeId: String) {
        val packet = DataPacket(
            senderNodeId = nodeId,
            targetNodeId = targetNodeId,
            payload = "PING_REQ",
            packetType = PacketType.PING
        ).signPacket(identityManager)

        networkManager.sendPacketToPeer(peerIp, peerPort, packet)
    }

    fun addManualPeer(ipAddress: String, port: Int) {
        if (ipAddress.isBlank() || port <= 0) return
        networkManager.addManualPeer(ipAddress, port)
    }

    fun storeDataBlock(payload: String) {
        if (payload.isBlank()) return
        val packet = DataPacket(
            senderNodeId = nodeId,
            targetNodeId = "NODE_STORAGE",
            payload = payload,
            packetType = PacketType.DATA_STORE
        ).signPacket(identityManager)

        viewModelScope.launch {
            repository.savePacket(packet, isVerified = true)
            // Broadcast to network for decentralized replication
            networkManager.broadcastPacket(payload, PacketType.DATA_STORE)
        }
    }

    fun deleteStoredBlock(id: String) {
        viewModelScope.launch {
            repository.deletePacket(id)
        }
    }

    fun clearLedger() {
        viewModelScope.launch {
            repository.clearLedger()
        }
    }

    fun retryConnection() {
        networkManager.startNetwork()
    }

    fun togglePowerSaverMode() {
        val current = isPowerSavingMode.value
        networkManager.setPowerSavingMode(!current)
    }

    fun toggleForegroundService() {
        val current = _isServiceRunning.value
        if (current) {
            networkManager.stopNetwork()
            val serviceIntent = Intent(context, P2pService::class.java).apply {
                action = P2pService.ACTION_STOP_SERVICE
            }
            context.startService(serviceIntent)
            _isServiceRunning.value = false
        } else {
            val serviceIntent = Intent(context, P2pService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            networkManager.startNetwork()
            _isServiceRunning.value = true
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeshViewModel(context) as T
        }
    }
}
