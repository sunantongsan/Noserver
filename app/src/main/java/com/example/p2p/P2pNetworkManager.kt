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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

enum class NetworkState {
    DISCONNECTED,
    STARTING,
    ACTIVE,
    POWER_SAVING
}

/**
 * Data class representing an ICE Candidate for NAT/Firewall Traversal.
 */
data class IceCandidate(
    val foundation: String,
    val componentId: Int = 1,
    val protocol: String = "UDP",
    val priority: Long = 2130706431,
    val ip: String,
    val port: Int,
    val type: CandidateType = CandidateType.HOST,
    val relAddr: String? = null,
    val relPort: Int? = null
) {
    enum class CandidateType {
        HOST,
        SRFLX,
        RELAY
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("foundation", foundation)
        json.put("componentId", componentId)
        json.put("protocol", protocol)
        json.put("priority", priority)
        json.put("ip", ip)
        json.put("port", port)
        json.put("type", type.name)
        if (relAddr != null) json.put("relAddr", relAddr)
        if (relPort != null) json.put("relPort", relPort)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): IceCandidate? {
            return try {
                val json = JSONObject(jsonStr)
                IceCandidate(
                    foundation = json.getString("foundation"),
                    componentId = json.optInt("componentId", 1),
                    protocol = json.optString("protocol", "UDP"),
                    priority = json.optLong("priority", 2130706431),
                    ip = json.getString("ip"),
                    port = json.getInt("port"),
                    type = try { CandidateType.valueOf(json.getString("type")) } catch (_: Exception) { CandidateType.HOST },
                    relAddr = if (json.has("relAddr")) json.getString("relAddr") else null,
                    relPort = if (json.has("relPort")) json.getInt("relPort") else null
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Production-ready P2P Network Manager supporting zero-server mDNS LAN discovery,
 * WebRTC-style STUN/ICE CGNAT NAT traversal, hole punching, and exponential backoff auto-reconnection.
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

    // Public STUN Servers for Mobile CGNAT Reflexive Address Resolution
    private val stunServers = listOf(
        Pair("stun.l.google.com", 19302),
        Pair("stun1.l.google.com", 19302),
        Pair("stun2.l.google.com", 19302),
        Pair("stun3.l.google.com", 19302),
        Pair("stun4.l.google.com", 19302)
    )

    private var serverSocket: ServerSocket? = null
    private var datagramSocket: DatagramSocket? = null
    private var localPort: Int = 8988
    private var localUdpPort: Int = 8989

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val discoveredPeersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val peerLastActivityMap = ConcurrentHashMap<String, Long>()

    private val _activePeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val activePeers: StateFlow<List<DiscoveredPeer>> = _activePeers.asStateFlow()

    private val _p2pState = MutableStateFlow<P2pState>(P2pState.Disconnected())
    val p2pState: StateFlow<P2pState> = _p2pState.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkState.DISCONNECTED)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<DataPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<DataPacket> = _incomingPackets.asSharedFlow()

    private val _isPowerSavingMode = MutableStateFlow(false)
    val isPowerSavingMode: StateFlow<Boolean> = _isPowerSavingMode.asStateFlow()

    private val _localReflexiveAddress = MutableStateFlow("")
    val localReflexiveAddress: StateFlow<String> = _localReflexiveAddress.asStateFlow()

    private val localCandidates = ConcurrentHashMap<String, IceCandidate>()

    private var tcpServerJob: Job? = null
    private var udpServerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var inactivityCleanupJob: Job? = null
    private var reconnectJob: Job? = null

    private var isExplicitlyStopped = false

    /**
     * Initializes local P2P Node server, performs STUN ICE candidate gathering, and registers mDNS service.
     */
    fun startNetwork() {
        if (_p2pState.value is P2pState.Connected || _p2pState.value is P2pState.Connecting) {
            Log.d(tag, "Network start requested but already starting or connected.")
            return
        }

        isExplicitlyStopped = false
        _p2pState.value = P2pState.Connecting(step = "Initializing Sockets & Local Transport")
        _networkState.value = NetworkState.STARTING

        scope.launch {
            try {
                startLocalSocketServer()
                startUdpSocketServer()

                _p2pState.value = P2pState.Connecting(step = "Gathering Local Host & STUN/ICE Candidates")
                gatherIceCandidates()

                registerMdnsService()
                startMdnsDiscovery()
                startHeartbeatTask()
                startInactiveConnectionCleanupTask()

                val peersCount = discoveredPeersMap.size
                val currentTransport = if (peersCount > 0) TransportType.MDNS_LAN else TransportType.NONE

                _p2pState.value = P2pState.Connected(
                    activePeersCount = peersCount,
                    transportType = currentTransport,
                    publicReflexiveAddress = _localReflexiveAddress.value,
                    localAddress = getLocalHostAddress()
                )
                _networkState.value = NetworkState.ACTIVE
                Log.i(tag, "P2P Network started successfully. Local IP: ${getLocalHostAddress()}, Reflexive IP: ${_localReflexiveAddress.value}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start P2P network: ${e.message}", e)
                _p2pState.value = P2pState.Failed(errorMessage = "Failed to start sockets: ${e.message}", cause = e)
                _networkState.value = NetworkState.DISCONNECTED
                attemptAutoReconnect("Initial network start failure: ${e.message}")
            }
        }
    }

    /**
     * Gracefully stops socket servers, discovery listeners, background jobs, and releases resources.
     */
    fun stopNetwork() {
        isExplicitlyStopped = true
        scope.launch {
            Log.i(tag, "Stopping P2P network manager gracefully...")
            reconnectJob?.cancel()
            stopMdnsDiscovery()
            unregisterMdnsService()
            closeLocalSocketServer()
            closeUdpSocketServer()
            heartbeatJob?.cancel()
            inactivityCleanupJob?.cancel()

            discoveredPeersMap.clear()
            peerLastActivityMap.clear()
            localCandidates.clear()

            _activePeers.value = emptyList()
            _localReflexiveAddress.value = ""
            _p2pState.value = P2pState.Disconnected(reason = "User stopped network")
            _networkState.value = NetworkState.DISCONNECTED
            Log.i(tag, "P2P network stopped completely.")
        }
    }

    /**
     * Called when underlying network interfaces switch (e.g., Wi-Fi to 4G/5G cellular or network drop).
     */
    fun handleNetworkChanged() {
        if (isExplicitlyStopped) return
        Log.i(tag, "Network interface change detected. Triggering ICE candidate re-gathering and reconnection...")
        scope.launch {
            attemptAutoReconnect("Network interface changed")
        }
    }

    /**
     * Enables or disables battery conservation throttling.
     */
    fun setPowerSavingMode(enabled: Boolean) {
        _isPowerSavingMode.value = enabled
        if (enabled && _networkState.value == NetworkState.ACTIVE) {
            _networkState.value = NetworkState.POWER_SAVING
            stopMdnsDiscovery()
            Log.d(tag, "Power Saver mode activated: mDNS continuous discovery paused.")
        } else if (!enabled && _networkState.value == NetworkState.POWER_SAVING) {
            _networkState.value = NetworkState.ACTIVE
            startMdnsDiscovery()
            Log.d(tag, "Power Saver mode deactivated: Resumed full discovery.")
        }
    }

    /**
     * Exponential Backoff Auto-Reconnect Logic.
     */
    private fun attemptAutoReconnect(reason: String) {
        if (isExplicitlyStopped) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val maxAttempts = 5
            val baseDelayMs = 1000L
            val maxDelayMs = 30000L
            val jitter = Random().nextInt(400) + 100

            for (attempt in 1..maxAttempts) {
                if (isExplicitlyStopped) break

                // Exponential delay calculation: min(maxDelay, base * 2^(attempt-1)) + jitter
                val exponentialFactor = 2.0.pow((attempt - 1).toDouble())
                val delayMs = min(maxDelayMs, (baseDelayMs * exponentialFactor).toLong()) + jitter

                _p2pState.value = P2pState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    nextRetryDelayMs = delayMs,
                    reason = reason
                )
                Log.w(tag, "Auto-reconnect attempt $attempt/$maxAttempts in ${delayMs}ms due to: $reason")

                delay(delayMs)

                try {
                    // Reset and rebind sockets if closed
                    if (serverSocket == null || serverSocket?.isClosed == true) {
                        startLocalSocketServer()
                    }
                    if (datagramSocket == null || datagramSocket?.isClosed == true) {
                        startUdpSocketServer()
                    }

                    // Re-gather ICE candidates for potential 4G/5G IP changes
                    gatherIceCandidates()
                    registerMdnsService()
                    startMdnsDiscovery()

                    // Probe known peers
                    probePeers()

                    val activeCount = discoveredPeersMap.size
                    val transport = if (activeCount > 0) TransportType.STUN_ICE_HOLE_PUNCH else TransportType.MDNS_LAN

                    _p2pState.value = P2pState.Connected(
                        activePeersCount = activeCount,
                        transportType = transport,
                        publicReflexiveAddress = _localReflexiveAddress.value,
                        localAddress = getLocalHostAddress()
                    )
                    _networkState.value = NetworkState.ACTIVE
                    Log.i(tag, "Successfully reconnected on attempt $attempt!")
                    return@launch
                } catch (e: Exception) {
                    Log.e(tag, "Reconnect attempt $attempt failed: ${e.message}")
                }
            }

            // Exhausted retries
            _p2pState.value = P2pState.Failed(
                errorMessage = "Exhausted $maxAttempts auto-reconnect attempts. Tap to retry.",
                isRecoverable = true
            )
            _networkState.value = NetworkState.DISCONNECTED
        }
    }

    /**
     * Broadcasts a signed DataPacket to all active mesh peers.
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
            performUdpHolePunch(peer.ipAddress, peer.port)
        }
    }

    /**
     * Direct message transmission to a target peer.
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
        performUdpHolePunch(peerIp, peerPort)
    }

    /**
     * Transmits a raw DataPacket over TCP or falls back to UDP STUN hole punching.
     */
    fun sendPacketToPeer(peerIp: String, peerPort: Int, packet: DataPacket) {
        scope.launch {
            var sentSuccess = false
            // Try TCP first
            try {
                Socket(peerIp, peerPort).use { socket ->
                    socket.soTimeout = 3000
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(packet.toJson())
                    sentSuccess = true
                    updatePeerLastSeen(packet.senderNodeId)
                }
            } catch (e: Exception) {
                Log.w(tag, "TCP send to $peerIp:$peerPort failed (${e.message}). Falling back to UDP STUN ICE hole punch...")
            }

            // Fallback to UDP STUN Hole Punching
            if (!sentSuccess) {
                sendUdpPacket(peerIp, peerPort, packet)
            }
        }
    }

    /**
     * Sends UDP packet to peer address to punch NAT hole.
     */
    private fun sendUdpPacket(peerIp: String, peerPort: Int, packet: DataPacket) {
        try {
            val socket = datagramSocket ?: return
            val bytes = packet.toJson().toByteArray(Charsets.UTF_8)
            val destAddress = InetAddress.getByName(peerIp)
            val datagram = DatagramPacket(bytes, bytes.size, destAddress, peerPort)
            socket.send(datagram)
            Log.d(tag, "Sent UDP packet (${packet.packetType}) to $peerIp:$peerPort")
        } catch (e: Exception) {
            Log.w(tag, "UDP send to $peerIp:$peerPort failed: ${e.message}")
        }
    }

    /**
     * Transmits NAT Hole Punching probe UDP packet to peer's reflexive address.
     */
    fun performUdpHolePunch(targetIp: String, targetPort: Int) {
        scope.launch {
            try {
                val punchPacket = DataPacket(
                    senderNodeId = identityManager.nodeId,
                    targetNodeId = "ICE_PUNCH",
                    payload = "NAT_HOLE_PUNCH_PROBE",
                    packetType = PacketType.ICE_PUNCH
                ).signPacket(identityManager)

                // Send 3 rapid UDP hole punch bursts to ensure mobile CGNAT state table opens
                for (i in 1..3) {
                    sendUdpPacket(targetIp, targetPort, punchPacket)
                    delay(50)
                }
            } catch (e: Exception) {
                Log.w(tag, "UDP Hole punch to $targetIp:$targetPort failed: ${e.message}")
            }
        }
    }

    /**
     * Gathers Host candidates (local IP/port) and Server Reflexive (`srflx`) candidates via STUN queries.
     */
    private suspend fun gatherIceCandidates() = withContext(Dispatchers.IO) {
        localCandidates.clear()

        // 1. Gather Host Candidate
        val localHostIp = getLocalHostAddress()
        if (localHostIp.isNotBlank()) {
            val hostCand = IceCandidate(
                foundation = "host1",
                componentId = 1,
                protocol = "UDP",
                priority = 2130706431,
                ip = localHostIp,
                port = localUdpPort,
                type = IceCandidate.CandidateType.HOST
            )
            localCandidates["host1"] = hostCand
        }

        // 2. Query STUN Servers for Server Reflexive (srflx) Candidate
        val socket = datagramSocket ?: return@withContext
        for ((stunHost, stunPort) in stunServers) {
            val reflexivePair = queryStunServer(stunHost, stunPort, socket)
            if (reflexivePair != null) {
                val (pubIp, pubPort) = reflexivePair
                val fullReflexiveStr = "$pubIp:$pubPort"
                _localReflexiveAddress.value = fullReflexiveStr

                val srflxCand = IceCandidate(
                    foundation = "srflx1",
                    componentId = 1,
                    protocol = "UDP",
                    priority = 1694498815,
                    ip = pubIp,
                    port = pubPort,
                    type = IceCandidate.CandidateType.SRFLX,
                    relAddr = localHostIp,
                    relPort = localUdpPort
                )
                localCandidates["srflx1"] = srflxCand
                Log.i(tag, "Discovered Public STUN Reflexive Address: $fullReflexiveStr via $stunHost")
                break // Got valid STUN binding
            }
        }
    }

    /**
     * Queries a STUN server via UDP DatagramSocket to extract mapped public IP & Port (RFC 5389 / RFC 3489).
     */
    private fun queryStunServer(stunHost: String, stunPort: Int, socket: DatagramSocket): Pair<String, Int>? {
        return try {
            val address = InetAddress.getByName(stunHost)
            val transactionId = ByteArray(12).apply { Random().nextBytes(this) }

            // 20-byte STUN Binding Request Header
            val request = ByteArray(20)
            request[0] = 0x00 // Binding Request
            request[1] = 0x01
            request[2] = 0x00 // Length
            request[3] = 0x00
            // Magic Cookie 0x2112A442
            request[4] = 0x21.toByte()
            request[5] = 0x12.toByte()
            request[6] = 0xA4.toByte()
            request[7] = 0x42.toByte()
            System.arraycopy(transactionId, 0, request, 8, 12)

            val sendPacket = DatagramPacket(request, request.size, address, stunPort)
            socket.soTimeout = 2500
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val response = receivePacket.data
            if (receivePacket.length >= 20) {
                val msgType = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
                if (msgType == 0x0101) { // Binding Response
                    var pos = 20
                    val length = receivePacket.length
                    while (pos + 4 <= length) {
                        val attrType = ((response[pos].toInt() and 0xFF) shl 8) or (response[pos + 1].toInt() and 0xFF)
                        val attrLen = ((response[pos + 2].toInt() and 0xFF) shl 8) or (response[pos + 3].toInt() and 0xFF)

                        if (attrType == 0x0020) { // XOR-MAPPED-ADDRESS
                            if (pos + 4 + attrLen <= length && attrLen >= 8) {
                                val family = response[pos + 5].toInt() and 0xFF
                                val xorPort = ((response[pos + 6].toInt() and 0xFF) shl 8) or (response[pos + 7].toInt() and 0xFF)
                                val port = xorPort xor 0x2112

                                if (family == 0x01) { // IPv4
                                    val ip1 = (response[pos + 8].toInt() and 0xFF) xor 0x21
                                    val ip2 = (response[pos + 9].toInt() and 0xFF) xor 0x12
                                    val ip3 = (response[pos + 10].toInt() and 0xFF) xor 0xA4
                                    val ip4 = (response[pos + 11].toInt() and 0xFF) xor 0x42
                                    val mappedIp = "$ip1.$ip2.$ip3.$ip4"
                                    return Pair(mappedIp, port)
                                }
                            }
                        } else if (attrType == 0x0001) { // MAPPED-ADDRESS
                            if (pos + 4 + attrLen <= length && attrLen >= 8) {
                                val family = response[pos + 5].toInt() and 0xFF
                                val port = ((response[pos + 6].toInt() and 0xFF) shl 8) or (response[pos + 7].toInt() and 0xFF)
                                if (family == 0x01) {
                                    val ip1 = response[pos + 8].toInt() and 0xFF
                                    val ip2 = response[pos + 9].toInt() and 0xFF
                                    val ip3 = response[pos + 10].toInt() and 0xFF
                                    val ip4 = response[pos + 11].toInt() and 0xFF
                                    val mappedIp = "$ip1.$ip2.$ip3.$ip4"
                                    return Pair(mappedIp, port)
                                }
                            }
                        }
                        pos += 4 + attrLen
                        if (attrLen % 4 != 0) {
                            pos += (4 - (attrLen % 4))
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(tag, "STUN lookup on $stunHost failed: ${e.message}")
            null
        }
    }

    private fun startLocalSocketServer() {
        tcpServerJob?.cancel()
        tcpServerJob = scope.launch {
            try {
                serverSocket = ServerSocket(0).apply { soTimeout = 0 }
                localPort = serverSocket?.localPort ?: 8988
                Log.d(tag, "TCP Server listening on port $localPort")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingTcpConnection(socket)
                }
            } catch (e: Exception) {
                if (isActive && !isExplicitlyStopped) {
                    Log.e(tag, "TCP Server socket error: ${e.message}")
                }
            }
        }
    }

    private fun handleIncomingTcpConnection(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val rawJson = reader.readLine()
                if (!rawJson.isNullOrBlank()) {
                    processIncomingPacket(rawJson, socket.inetAddress.hostAddress ?: "", socket.port)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error reading incoming TCP socket data: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun closeLocalSocketServer() {
        tcpServerJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun startUdpSocketServer() {
        udpServerJob?.cancel()
        udpServerJob = scope.launch {
            try {
                datagramSocket = DatagramSocket(0).apply { soTimeout = 0 }
                localUdpPort = datagramSocket?.localPort ?: 8989
                Log.d(tag, "UDP Listener listening on port $localUdpPort")

                val buffer = ByteArray(4096)
                while (isActive) {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    datagramSocket?.receive(datagram)
                    val rawStr = String(datagram.data, 0, datagram.length, Charsets.UTF_8)
                    val senderIp = datagram.address.hostAddress ?: ""
                    val senderPort = datagram.port
                    processIncomingPacket(rawStr, senderIp, senderPort)
                }
            } catch (e: Exception) {
                if (isActive && !isExplicitlyStopped) {
                    Log.e(tag, "UDP Server error: ${e.message}")
                }
            }
        }
    }

    private fun closeUdpSocketServer() {
        udpServerJob?.cancel()
        try { datagramSocket?.close() } catch (_: Exception) {}
        datagramSocket = null
    }

    private fun processIncomingPacket(rawJson: String, senderIp: String, senderPort: Int) {
        val packet = DataPacket.fromJson(rawJson) ?: return
        if (packet.senderNodeId == identityManager.nodeId) return // Ignore self-echo

        updatePeerLastSeen(packet.senderNodeId)
        scope.launch { _incomingPackets.emit(packet) }

        when (packet.packetType) {
            PacketType.PING -> {
                val pongPacket = DataPacket(
                    senderNodeId = identityManager.nodeId,
                    targetNodeId = packet.senderNodeId,
                    payload = "PONG_ACK",
                    packetType = PacketType.PONG
                ).signPacket(identityManager)
                sendPacketToPeer(senderIp, senderPort, pongPacket)
            }
            PacketType.ICE_PUNCH -> {
                Log.d(tag, "Received UDP ICE Hole Punch from ${packet.senderNodeId} ($senderIp:$senderPort)")
                // Acknowledge hole punch
                val pong = DataPacket(
                    senderNodeId = identityManager.nodeId,
                    targetNodeId = packet.senderNodeId,
                    payload = "ICE_PUNCH_ACK",
                    packetType = PacketType.PONG
                ).signPacket(identityManager)
                sendUdpPacket(senderIp, senderPort, pong)
            }
            PacketType.ICE_OFFER -> {
                // Respond with ICE Answer containing local candidates
                val srflx = localCandidates["srflx1"]
                val answerPayload = srflx?.toJson() ?: localCandidates["host1"]?.toJson() ?: ""
                val answerPacket = DataPacket(
                    senderNodeId = identityManager.nodeId,
                    targetNodeId = packet.senderNodeId,
                    payload = answerPayload,
                    packetType = PacketType.ICE_ANSWER
                ).signPacket(identityManager)
                sendPacketToPeer(senderIp, senderPort, answerPacket)
            }
            else -> {}
        }
    }

    private fun registerMdnsService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = identityManager.nodeId
            setServiceType(this@P2pNetworkManager.serviceType)
            port = localPort
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("nodeId", identityManager.nodeId)
                setAttribute("publicKey", identityManager.publicKeyHex)
                if (_localReflexiveAddress.value.isNotBlank()) {
                    setAttribute("srflx", _localReflexiveAddress.value)
                }
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(tag, "mDNS Service Registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.e(tag, "mDNS Registration failed code: $arg1")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(tag, "mDNS Service Unregistered")
            }
            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Log.e(tag, "mDNS Unregistration failed: $arg1")
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
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null
    }

    private fun startMdnsDiscovery() {
        if (_isPowerSavingMode.value) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "mDNS Discovery Started")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType && service.serviceName != identityManager.nodeId) {
                    resolveService(service)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredPeersMap.remove(service.serviceName)
                peerLastActivityMap.remove(service.serviceName)
                updateActivePeersList()
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "mDNS Discovery Stopped")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "mDNS Start discovery failed: $errorCode")
                stopMdnsDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "mDNS Stop discovery failed: $errorCode")
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
                    if (bytes != null) pubKey = String(bytes)
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
                updatePeerLastSeen(name)
                updateActivePeersList()
                performUdpHolePunch(peer.ipAddress, peer.port)
            }
        })
    }

    private fun stopMdnsDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    private fun updateActivePeersList() {
        val peers = discoveredPeersMap.values.toList()
        _activePeers.value = peers

        if (!isExplicitlyStopped) {
            val transport = if (peers.isNotEmpty()) TransportType.MDNS_LAN else TransportType.NONE
            _p2pState.value = P2pState.Connected(
                activePeersCount = peers.size,
                transportType = transport,
                publicReflexiveAddress = _localReflexiveAddress.value,
                localAddress = getLocalHostAddress()
            )
        }
    }

    private fun updatePeerLastSeen(nodeId: String) {
        val now = System.currentTimeMillis()
        peerLastActivityMap[nodeId] = now
        discoveredPeersMap[nodeId]?.let {
            discoveredPeersMap[nodeId] = it.copy(lastSeen = now, isConnected = true)
        }
    }

    /**
     * Periodically probes peers with PING packets.
     */
    private fun startHeartbeatTask() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                val interval = if (_isPowerSavingMode.value) 30000L else 12000L
                delay(interval)

                probePeers()
            }
        }
    }

    private fun probePeers() {
        val peers = discoveredPeersMap.values.toList()
        for (peer in peers) {
            sendDirectPacket(peer.ipAddress, peer.port, "PING_HEARTBEAT", peer.nodeId)
        }
    }

    /**
     * Battery & Data Safe Inactive Connection Reaper Task.
     * Purges stale peers that haven't responded or shown activity for > 60 seconds.
     */
    private fun startInactiveConnectionCleanupTask() {
        inactivityCleanupJob?.cancel()
        inactivityCleanupJob = scope.launch {
            while (isActive) {
                delay(15000L) // Check every 15s

                val now = System.currentTimeMillis()
                val inactiveTimeoutMs = 60000L
                var peersRemoved = false

                val iterator = discoveredPeersMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val lastSeen = peerLastActivityMap[entry.key] ?: entry.value.lastSeen
                    if (now - lastSeen > inactiveTimeoutMs) {
                        Log.w(tag, "Closing inactive peer connection: ${entry.key} (inactive for ${(now - lastSeen) / 1000}s)")
                        iterator.remove()
                        peerLastActivityMap.remove(entry.key)
                        peersRemoved = true
                    }
                }

                if (peersRemoved) {
                    updateActivePeersList()
                }
            }
        }
    }

    /**
     * Manual Peer Addition with STUN Hole Punching fallback.
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
        updatePeerLastSeen(nodeId)
        updateActivePeersList()

        // Send handshake packet & perform UDP hole punch
        val handshakePacket = DataPacket(
            senderNodeId = identityManager.nodeId,
            targetNodeId = nodeId,
            payload = "HANDSHAKE_INIT",
            packetType = PacketType.HANDSHAKE
        ).signPacket(identityManager)

        sendPacketToPeer(ipAddress, port, handshakePacket)
        performUdpHolePunch(ipAddress, port)
    }

    private fun getLocalHostAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
