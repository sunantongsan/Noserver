package com.example.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Proof-of-Resource metrics snapshot.
 */
data class YieldMetrics(
    val nodeUptimeSeconds: Long = 0L,
    val storageProvidedBytes: Long = 0L,
    val packetsRoutedCount: Long = 0L,
    val totalEarnedTokens: Double = 0.0,
    val hourlyYieldRate: Double = 0.0,
    val rankTierName: String = "Genesis Node"
) {
    /**
     * Formats storage in human-readable KB, MB, or GB.
     */
    fun getFormattedStorage(): String {
        val kb = storageProvidedBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            else -> String.format("%.1f KB", kb.coerceAtLeast(0.0))
        }
    }

    /**
     * Formats node uptime in hours, minutes, and seconds.
     */
    fun getFormattedUptime(): String {
        val hours = nodeUptimeSeconds / 3600
        val minutes = (nodeUptimeSeconds % 3600) / 60
        val seconds = nodeUptimeSeconds % 60
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }

    /**
     * Formats earned tokens with 4 decimal precision.
     */
    fun getFormattedTokens(): String {
        return String.format("%.4f MESH", totalEarnedTokens)
    }
}

/**
 * Zero-Gas Proof-of-Resource Incentive Engine.
 * Tracks node uptime, active storage hosting, and P2P packet routing contributions.
 * Calculates fair reward distribution locally using deterministic mathematical formulas without central gas fees.
 */
class YieldEngine(
    private val context: Context,
    private val identityManager: NodeIdentityManager
) {

    private val tag = "YieldEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var tickerJob: Job? = null

    private val prefs = context.getSharedPreferences("noserver_yield_prefs", Context.MODE_PRIVATE)

    private val _yieldMetrics = MutableStateFlow(loadInitialMetrics())
    val yieldMetrics: StateFlow<YieldMetrics> = _yieldMetrics.asStateFlow()

    init {
        startYieldEngineTicker()
    }

    /**
     * Starts background timer accumulating node uptime and computing hourly token yields.
     */
    fun startYieldEngineTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L) // 1 second ticker tick

                val current = _yieldMetrics.value
                val newUptime = current.nodeUptimeSeconds + 1

                // Reward Formula:
                // Base Uptime Reward: 0.0001 tokens/sec
                // Storage Reward: (storageBytes / 10MB) * 0.00001 tokens/sec
                // Routing Bonus: 0.0005 per routed packet
                val storageMb = current.storageProvidedBytes / (1024.0 * 1024.0)
                val secondYield = 0.0001 + (storageMb * 0.00001)
                val newTokens = current.totalEarnedTokens + secondYield
                val hourlyRate = secondYield * 3600.0

                val tier = when {
                    newTokens >= 100.0 -> "Master Mesh Node"
                    newTokens >= 25.0 -> "Super Peer Node"
                    newTokens >= 5.0 -> "Active Relay Node"
                    else -> "Genesis Node"
                }

                val updated = current.copy(
                    nodeUptimeSeconds = newUptime,
                    totalEarnedTokens = newTokens,
                    hourlyYieldRate = hourlyRate,
                    rankTierName = tier
                )

                _yieldMetrics.value = updated

                // Persist stats every 30 seconds
                if (newUptime % 30 == 0L) {
                    saveMetrics(updated)
                }
            }
        }
    }

    /**
     * Records a newly routed P2P mesh data packet and updates token reward calculation.
     */
    fun recordPacketRouted(count: Long = 1) {
        val current = _yieldMetrics.value
        val packetReward = count * 0.0005 // 0.0005 MESH tokens per packet
        val updated = current.copy(
            packetsRoutedCount = current.packetsRoutedCount + count,
            totalEarnedTokens = current.totalEarnedTokens + packetReward
        )
        _yieldMetrics.value = updated
        saveMetrics(updated)
        Log.d(tag, "Recorded $count routed packet(s). Total routed: ${updated.packetsRoutedCount}")
    }

    /**
     * Updates storage provided metric when local files or CRDT data chunks are created/stored.
     */
    fun updateStorageProvided(bytes: Long) {
        val current = _yieldMetrics.value
        val updated = current.copy(storageProvidedBytes = bytes.coerceAtLeast(0L))
        _yieldMetrics.value = updated
        saveMetrics(updated)
        Log.d(tag, "Updated storage provided: ${updated.getFormattedStorage()}")
    }

    /**
     * Gracefully stops ticker and persists final state.
     */
    fun stop() {
        tickerJob?.cancel()
        saveMetrics(_yieldMetrics.value)
    }

    private fun loadInitialMetrics(): YieldMetrics {
        val uptime = prefs.getLong("uptime_seconds", 0L)
        val storage = prefs.getLong("storage_bytes", 102400L) // Default 100 KB initialized
        val packets = prefs.getLong("packets_routed", 0L)
        val tokens = java.lang.Double.longBitsToDouble(prefs.getLong("earned_tokens_bits", java.lang.Double.doubleToLongBits(0.0)))
        val tier = prefs.getString("rank_tier", "Genesis Node") ?: "Genesis Node"

        return YieldMetrics(
            nodeUptimeSeconds = uptime,
            storageProvidedBytes = storage,
            packetsRoutedCount = packets,
            totalEarnedTokens = tokens,
            rankTierName = tier
        )
    }

    private fun saveMetrics(metrics: YieldMetrics) {
        prefs.edit().apply {
            putLong("uptime_seconds", metrics.nodeUptimeSeconds)
            putLong("storage_bytes", metrics.storageProvidedBytes)
            putLong("packets_routed", metrics.packetsRoutedCount)
            putLong("earned_tokens_bits", java.lang.Double.doubleToLongBits(metrics.totalEarnedTokens))
            putString("rank_tier", metrics.rankTierName)
            apply()
        }
    }
}
