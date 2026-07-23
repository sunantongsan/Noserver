package com.example.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

enum class NetworkState {
    DISCONNECTED,
    STARTING,
    ACTIVE,
    POWER_SAVING
}

/**
 * P2pNetworkManager coordinates zero-server local peer discovery via mDNS (NsdManager)
 * and direct TCP socket DataChannel transport between Android nodes.
 */
class P2pNetworkManager(
    private val context: Context,
    private val identityManager: NodeIdentityManager
) {

    private val tag = "P2pNetworkManager"
    private val serviceType = "_livingmesh._tcp."
    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 8988

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val discoveredPeersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _activePeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val activePeers: StateFlow<List<DiscoveredPeer>> = _activePeers.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkState.DISCONNECTED)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<DataPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<DataPacket> = _incomingPackets.asSharedFlow()

    private val _isPowerSavingMode = MutableStateFlow(false)
    val isPowerSavingMode: StateFlow<Boolean> = _isPowerSavingMode.asStateFlow()

    private var serverJob: Job? = null
    private var heartbeatJob: Job? = null

    /**
     * Initializes local P2P Node server and registers mDNS discovery service.
     */
    fun startNetwork() {
        if (_networkState.value == NetworkState.ACTIVE || _networkState.value == NetworkState.STARTING) return
        _networkState.value = NetworkState.STARTING

        scope.launch {
            try {
                startLocalSocketServer()
                registerMdnsService()
                startMdnsDiscovery()
                startHeartbeatTask()
                _networkState.value = NetworkState.ACTIVE
            } catch (e: Exception) {
                Log.e(tag, "Failed to start P2P network: ${e.message}")
                _networkState.value = NetworkState.DISCONNECTED
            }
        }
    }

    /**
     * Gracefully stops socket server, discovery listeners, and background tasks.
     */
    fun stopNetwork() {
        scope.launch {
            stopMdnsDiscovery()
            unregisterMdnsService()
            closeLocalSocketServer()
            heartbeatJob?.cancel()
            discoveredPeersMap.clear()
            _activePeers.value = emptyList()
            _networkState.value = NetworkState.DISCONNECTED
        }
    }

    /**
     * Enables or disables battery conservation throttling.
     */
    fun setPowerSavingMode(enabled: Boolean) {
        _isPowerSavingMode.value = enabled
        if (enabled && _networkState.value == NetworkState.ACTIVE) {
            _networkState.value = NetworkState.POWER_SAVING
            // Slow down scanning or suspend background mDNS discovery cycles
            stopMdnsDiscovery()
        } else if (!enabled && _networkState.value == NetworkState.POWER_SAVING) {
            _networkState.value = NetworkState.ACTIVE
            startMdnsDiscovery()
        }
    }

    /**
     * Broadcasts a signed DataPacket to all currently discovered P2P peers.
     */
    fun broadcastPacket(payload: String, packetType: PacketType = PacketType.CHAT_MESSAGE) {
        val packet = DataPacket(
            senderNodeId = identityManager.nodeId,
            targetNodeId = "BROADCAST",
            payload = payload,
            packetType = packetType
        ).signPacket(identityManager)

        val peers = discoveredPeersMap.values.toList()
        for (peer in peers) {
            sendPacketToPeer(peer.ipAddress, peer.port, packet)
        }
    }

    /**
     * Direct message transmission to a specific target peer address.
     */
    fun sendDirectPacket(
        peerIp: String,
        peerPort: Int,
        payload: String,
        targetNodeId: String = "DIRECT"
    ) {
        val packet = DataPacket(
            senderNodeId = identityManager.nodeId,
            targetNodeId = targetNodeId,
            payload = payload,
            packetType = PacketType.CHAT_MESSAGE
        ).signPacket(identityManager)

        sendPacketToPeer(peerIp, peerPort, packet)
    }

    /**
     * Transmits a raw DataPacket over a TCP socket connection.
     */
    fun sendPacketToPeer(peerIp: String, peerPort: Int, packet: DataPacket) {
        scope.launch {
            try {
                Socket(peerIp, peerPort).use { socket ->
                    socket.soTimeout = 3000
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(packet.toJson())
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to send packet to $peerIp:$peerPort - ${e.message}")
            }
        }
    }

    /**
     * Starts the TCP ServerSocket listener for incoming P2P mesh data packets.
     */
    private fun startLocalSocketServer() {
        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                // Bind dynamically to available port
                serverSocket = ServerSocket(0).apply {
                    soTimeout = 0 // Blocking accept
                }
                localPort = serverSocket?.localPort ?: 8988
                Log.d(tag, "Local P2P Node listening on TCP port $localPort")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingConnection(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(tag, "Server socket error: ${e.message}")
                }
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val rawJson = reader.readLine()
                if (!rawJson.isNull_or_blank()) {
                    val packet = DataPacket.fromJson(rawJson)
                    if (packet != null) {
                        _incomingPackets.emit(packet)

                        // Respond to PING with PONG
                        if (packet.packetType == PacketType.PING) {
                            val pongPacket = DataPacket(
                                senderNodeId = identityManager.nodeId,
                                targetNodeId = packet.senderNodeId,
                                payload = "PONG_ACK",
                                packetType = PacketType.PONG
                            ).signPacket(identityManager)
                            sendPacketToPeer(socket.inetAddress.hostAddress ?: "", localPort, pongPacket)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error reading incoming socket data: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun closeLocalSocketServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    /**
     * Advertises this node on local network via mDNS (Network Service Discovery).
     */
    private fun registerMdnsService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = identityManager.nodeId
            setServiceType(this@P2pNetworkManager.serviceType)
            port = localPort
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("nodeId", identityManager.nodeId)
                setAttribute("publicKey", identityManager.publicKeyHex)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(tag, "mDNS Service Registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.e(tag, "mDNS Service Registration failed: $arg1")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(tag, "mDNS Service Unregistered")
            }

            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.e(tag, "mDNS Service Unregistration failed: $arg1")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error registering NSD service: ${e.message}")
        }
    }

    private fun unregisterMdnsService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (_: Exception) {}
        }
        registrationListener = null
    }

    /**
     * Scans local Wi-Fi / LAN for neighboring Living Mesh nodes.
     */
    private fun startMdnsDiscovery() {
        if (_isPowerSavingMode.value) return // Skip continuous discovery in power saver mode

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "mDNS Peer Discovery Started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType && service.serviceName != identityManager.nodeId) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredPeersMap.remove(service.serviceName)
                updateActivePeersList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "mDNS Peer Discovery Stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed: Error $errorCode")
                stopMdnsDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Stop Discovery failed: Error $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error starting NSD discovery: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host: InetAddress = serviceInfo.host ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName

                var pubKey = ""
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && serviceInfo.attributes != null) {
                    val bytes = serviceInfo.attributes["publicKey"]
                    if (bytes != null) {
                        pubKey = String(bytes)
                    }
                }

                val peer = DiscoveredPeer(
                    nodeId = name,
                    serviceName = name,
                    ipAddress = host.hostAddress ?: "",
                    port = port,
                    publicKeyHex = pubKey,
                    lastSeen = System.currentTimeMillis(),
                    isConnected = true
                )

                discoveredPeersMap[name] = peer
                updateActivePeersList()
            }
        })
    }

    private fun stopMdnsDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    private fun updateActivePeersList() {
        _activePeers.value = discoveredPeersMap.values.toList()
    }

    private fun startHeartbeatTask() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                val interval = if (_isPowerSavingMode.value) 30000L else 10000L
                delay(interval)

                // Remove stale peers (> 60s inactive)
                val now = System.currentTimeMillis()
                val iterator = discoveredPeersMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastSeen > 60000) {
                        iterator.remove()
                    }
                }
                updateActivePeersList()
            }
        }
    }

    /**
     * Allows adding a manual peer via direct IP & Port if mDNS is limited on certain Wi-Fi routers.
     */
    fun addManualPeer(ipAddress: String, port: Int) {
        val nodeId = "NODE-MANUAL-${ipAddress.replace(".", "")}"
        val peer = DiscoveredPeer(
            nodeId = nodeId,
            serviceName = "Manual $ipAddress",
            ipAddress = ipAddress,
            port = port,
            isConnected = true
        )
        discoveredPeersMap[nodeId] = peer
        updateActivePeersList()

        // Send handshake packet
        val handshakePacket = DataPacket(
            senderNodeId = identityManager.nodeId,
            targetNodeId = nodeId,
            payload = "HANDSHAKE_INIT",
            packetType = PacketType.HANDSHAKE
        ).signPacket(identityManager)

        sendPacketToPeer(ipAddress, port, handshakePacket)
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
