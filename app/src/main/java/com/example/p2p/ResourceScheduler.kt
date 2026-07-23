package com.example.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Energy policy state representing current system energy and network constraints.
 */
data class EnergyPolicyState(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isOnWifi: Boolean = false,
    val isBatteryLow: Boolean = false,
    val isHeavyTaskAllowed: Boolean = true,
    val statusMessage: String = "Battery Safe: Operational"
)

/**
 * Smart Resource & Energy Scheduler for zero-gas serverless P2P mesh operations.
 * Monitors battery level, charging state, and Wi-Fi interface availability via
 * Android BroadcastReceivers and ConnectivityManager. Ensures heavy P2P sync and
 * compute background tasks execute safely without draining hardware battery or cellular data.
 */
class ResourceScheduler(
    private val context: Context
) {
    private val tag = "ResourceScheduler"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _energyPolicy = MutableStateFlow(EnergyPolicyState())
    val energyPolicy: StateFlow<EnergyPolicyState> = _energyPolicy.asStateFlow()

    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startMonitoring()
    }

    /**
     * Starts continuous background monitoring of device power and network interfaces.
     */
    fun startMonitoring() {
        registerBatteryReceiver()
        registerNetworkCallback()
    }

    /**
     * Stops receivers and unbinds callbacks on service/lifecycle cleanup.
     */
    fun stopMonitoring() {
        unregisterBatteryReceiver()
        unregisterNetworkCallback()
    }

    /**
     * Helper check determining if a heavy P2P background operation (e.g. CRDT full re-sync, large file sharding) is permitted.
     */
    fun canExecuteHeavyTask(): Boolean {
        return _energyPolicy.value.isHeavyTaskAllowed
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val isLow = batteryPct <= 20 && !isCharging

                    updateEnergyPolicy(
                        batteryLevel = batteryPct,
                        isCharging = isCharging,
                        isBatteryLow = isLow
                    )
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(tag, "Unregister battery receiver error: ${e.message}")
            }
        }
        batteryReceiver = null
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    updateEnergyPolicy(isOnWifi = hasWifi)
                }

                override fun onLost(network: Network) {
                    updateEnergyPolicy(isOnWifi = false)
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback as ConnectivityManager.NetworkCallback)
        } catch (e: Exception) {
            Log.e(tag, "Error registering network connectivity callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(tag, "Unregister network callback error: ${e.message}")
            }
        }
        networkCallback = null
    }

    @Synchronized
    private fun updateEnergyPolicy(
        batteryLevel: Int = _energyPolicy.value.batteryLevel,
        isCharging: Boolean = _energyPolicy.value.isCharging,
        isOnWifi: Boolean = _energyPolicy.value.isOnWifi,
        isBatteryLow: Boolean = _energyPolicy.value.isBatteryLow
    ) {
        // Heavy background task rule:
        // Allowed if charging OR if battery > 30% and on Wi-Fi
        val heavyAllowed = isCharging || (batteryLevel > 30 && isOnWifi)

        val statusMsg = when {
            isCharging -> "Battery Safe: Charging • Heavy Tasks Allowed"
            isBatteryLow -> "Battery Saver: Active (<20%) • P2P Sync Throttled"
            isOnWifi -> "Wi-Fi Connected • Balanced Energy Mode"
            else -> "Cellular / Mobile CGNAT • Conservative Data Mode"
        }

        val updated = EnergyPolicyState(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isOnWifi = isOnWifi,
            isBatteryLow = isBatteryLow,
            isHeavyTaskAllowed = heavyAllowed,
            statusMessage = statusMsg
        )

        _energyPolicy.value = updated
        Log.d(tag, "Updated Energy Policy: $updated")
    }
}
