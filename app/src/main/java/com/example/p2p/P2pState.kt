package com.example.p2p

/**
 * Transport mechanisms utilized for P2P mesh network connection.
 */
enum class TransportType {
    MDNS_LAN,
    STUN_ICE_HOLE_PUNCH,
    WEBSOCKET_RELAY,
    DIRECT_TCP,
    NONE
}

/**
 * Sealed class representing detailed P2P network connection states for UI state management and service monitoring.
 */
sealed class P2pState {

    abstract val isConnected: Boolean
    abstract val description: String

    /**
     * Initial or stopped state where the node is idle or offline.
     */
    data class Disconnected(
        val reason: String = "Idle"
    ) : P2pState() {
        override val isConnected: Boolean = false
        override val description: String = "Disconnected ($reason)"
    }

    /**
     * Actively attempting to establish node listeners, STUN candidate gathering, or peer handshakes.
     */
    data class Connecting(
        val step: String = "Gathering STUN/ICE Candidates...",
        val attempt: Int = 1,
        val transportType: TransportType = TransportType.NONE
    ) : P2pState() {
        override val isConnected: Boolean = false
        override val description: String = "Connecting: $step (Attempt $attempt)"
    }

    /**
     * Node is active and connected to 1 or more peers via LAN, STUN/ICE hole punch, or WebSocket relay.
     */
    data class Connected(
        val activePeersCount: Int = 0,
        val transportType: TransportType = TransportType.MDNS_LAN,
        val publicReflexiveAddress: String = "",
        val localAddress: String = ""
    ) : P2pState() {
        override val isConnected: Boolean = true
        override val description: String = if (publicReflexiveAddress.isNotBlank()) {
            "Connected via ${transportType.name} ($activePeersCount peer(s) • Pub IP: $publicReflexiveAddress)"
        } else {
            "Connected via ${transportType.name} ($activePeersCount peer(s))"
        }
    }

    /**
     * Connection was dropped (e.g., Wi-Fi to 4G/5G handoff, socket timeout) and auto-reconnecting with exponential backoff.
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int = 5,
        val nextRetryDelayMs: Long,
        val reason: String = "Network connection loss"
    ) : P2pState() {
        override val isConnected: Boolean = false
        override val description: String = "Reconnecting ($attempt/$maxAttempts in ${nextRetryDelayMs / 1000}s)... Reason: $reason"
    }

    /**
     * Critical failure or exhausted reconnect attempts.
     */
    data class Failed(
        val errorMessage: String,
        val isRecoverable: Boolean = true,
        val cause: Throwable? = null
    ) : P2pState() {
        override val isConnected: Boolean = false
        override val description: String = "Network Error: $errorMessage"
    }
}
